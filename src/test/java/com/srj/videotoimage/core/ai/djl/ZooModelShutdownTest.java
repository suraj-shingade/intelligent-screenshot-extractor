/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.ai.djl;

import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import com.srj.videotoimage.core.model.Frame;
import com.srj.videotoimage.core.model.FrameMetadata;
import com.srj.videotoimage.testsupport.TestImages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Shutdown racing model use. Every test here pins a thread at a precise point
 * with a latch, so the interleaving is forced rather than hoped for.
 *
 * <p>The model and predictor are Mockito stand-ins: what is under test is the
 * provider's locking, not DJL.</p>
 */
class ZooModelShutdownTest {

    private static final long WAIT_SECONDS = 5;

    private final ExecutorService pool = Executors.newCachedThreadPool();

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    private static Frame frame() {
        return new Frame(0L, Duration.ZERO, TestImages.noise(32, 32, 1L));
    }

    private static Classifications onePrediction() {
        return new Classifications(List.of("whiteboard"), List.of(0.9d));
    }

    /**
     * A provider whose model comes from the test, optionally held back at the
     * moment of loading so the test can act while the load is in flight.
     */
    private static final class ControlledProvider extends AbstractZooModelProvider<Classifications> {

        private final ZooModel<Image, Classifications> model;
        private final CountDownLatch loadStarted = new CountDownLatch(1);
        private final CountDownLatch releaseLoad;
        private final AtomicInteger loads = new AtomicInteger();

        private ControlledProvider(ZooModel<Image, Classifications> model, CountDownLatch releaseLoad) {
            super(3, 0.1d);
            this.model = model;
            this.releaseLoad = releaseLoad;
        }

        @Override
        public String name() {
            return "controlled";
        }

        @Override
        protected Criteria<Image, Classifications> criteria() {
            return Criteria.builder().setTypes(Image.class, Classifications.class).build();
        }

        @Override
        protected ZooModel<Image, Classifications> loadModel() throws Exception {
            loads.incrementAndGet();
            loadStarted.countDown();
            if (releaseLoad != null && !releaseLoad.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test never released the load");
            }
            return model;
        }

        @Override
        protected FrameMetadata toMetadata(Classifications prediction) {
            return new FrameMetadata(List.of("seen"), List.of(), List.of(), java.util.Map.of());
        }
    }

    @SuppressWarnings("unchecked")
    private static ZooModel<Image, Classifications> modelWith(Predictor<Image, Classifications> predictor) {
        ZooModel<Image, Classifications> model = mock(ZooModel.class);
        when(model.newPredictor()).thenReturn(predictor);
        return model;
    }

    @SuppressWarnings("unchecked")
    private static Predictor<Image, Classifications> predictor() {
        return mock(Predictor.class);
    }

    // -- The race Copilot described --------------------------------------------

    @Test
    void closingMidLoadNeitherWaitsForTheDownloadNorLeaksTheModel() throws Exception {
        Predictor<Image, Classifications> predictor = predictor();
        ZooModel<Image, Classifications> model = modelWith(predictor);
        CountDownLatch releaseLoad = new CountDownLatch(1);
        ControlledProvider provider = new ControlledProvider(model, releaseLoad);

        // A job thread starts the first inference, which begins the load...
        Future<FrameMetadata> inference = pool.submit(() -> provider.infer(frame()));
        assertThat(provider.loadStarted.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        // ...and shutdown arrives while the weights are still downloading.
        Future<?> shutdown = pool.submit(provider::close);
        shutdown.get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(shutdown.isDone())
                .as("close() must not sit behind a download that can take minutes")
                .isTrue();

        // The download then completes, after close() has already returned.
        releaseLoad.countDown();
        FrameMetadata result = inference.get(WAIT_SECONDS, TimeUnit.SECONDS);

        verify(model).close();
        assertThat(provider.loaded())
                .as("a model that finished loading after close must not be kept")
                .isFalse();
        assertThat(result).isEqualTo(FrameMetadata.empty());
        verify(model, never()).newPredictor();
    }

    @Test
    void closingAfterALoadReleasesTheModel() throws Exception {
        Predictor<Image, Classifications> predictor = predictor();
        when(predictor.predict(any())).thenReturn(onePrediction());
        ZooModel<Image, Classifications> model = modelWith(predictor);
        ControlledProvider provider = new ControlledProvider(model, null);

        assertThat(provider.infer(frame()).tags()).containsExactly("seen");
        provider.close();

        verify(predictor).close();
        verify(model).close();
        assertThat(provider.loaded()).isFalse();
    }

    // -- The adjacent race: a prediction running as close() arrives ------------

    @Test
    void closeWaitsForAPredictionInProgressBeforeFreeingTheModel() throws Exception {
        CountDownLatch predicting = new CountDownLatch(1);
        CountDownLatch finishPrediction = new CountDownLatch(1);
        Predictor<Image, Classifications> predictor = predictor();
        when(predictor.predict(any())).thenAnswer(call -> {
            predicting.countDown();
            finishPrediction.await(WAIT_SECONDS, TimeUnit.SECONDS);
            return onePrediction();
        });
        ZooModel<Image, Classifications> model = modelWith(predictor);
        ControlledProvider provider = new ControlledProvider(model, null);

        Future<FrameMetadata> inference = pool.submit(() -> provider.infer(frame()));
        assertThat(predicting.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        Future<?> shutdown = pool.submit(provider::close);

        // Freeing native memory under a running prediction is a use-after-free,
        // so close() must still be waiting here.
        Thread.sleep(200);
        assertThat(shutdown.isDone())
                .as("close() must wait for the prediction already in progress")
                .isFalse();
        verify(model, never()).close();

        finishPrediction.countDown();
        shutdown.get(WAIT_SECONDS, TimeUnit.SECONDS);

        assertThat(inference.get(WAIT_SECONDS, TimeUnit.SECONDS).tags()).containsExactly("seen");
        verify(predictor).close();
        verify(model).close();
    }

    @Test
    void nothingPredictsAfterClose() throws Exception {
        Predictor<Image, Classifications> predictor = predictor();
        when(predictor.predict(any())).thenReturn(onePrediction());
        ZooModel<Image, Classifications> model = modelWith(predictor);
        ControlledProvider provider = new ControlledProvider(model, null);

        provider.infer(frame());
        provider.close();
        FrameMetadata afterClose = provider.infer(frame());

        assertThat(afterClose).isEqualTo(FrameMetadata.empty());
        verify(predictor, org.mockito.Mockito.times(1)).predict(any());
    }

    @Test
    void closingTwiceReleasesEverythingExactlyOnce() throws Exception {
        Predictor<Image, Classifications> predictor = predictor();
        when(predictor.predict(any())).thenReturn(onePrediction());
        ZooModel<Image, Classifications> model = modelWith(predictor);
        ControlledProvider provider = new ControlledProvider(model, null);

        provider.infer(frame());
        provider.close();
        provider.close();

        verify(predictor, org.mockito.Mockito.times(1)).close();
        verify(model, org.mockito.Mockito.times(1)).close();
    }

    @Test
    void concurrentFirstInferencesDownloadTheModelOnce() throws Exception {
        Predictor<Image, Classifications> predictor = predictor();
        when(predictor.predict(any())).thenReturn(onePrediction());
        ZooModel<Image, Classifications> model = modelWith(predictor);
        CountDownLatch releaseLoad = new CountDownLatch(1);
        ControlledProvider provider = new ControlledProvider(model, releaseLoad);

        List<Future<FrameMetadata>> inferences = List.of(
                pool.submit(() -> provider.infer(frame())),
                pool.submit(() -> provider.infer(frame())),
                pool.submit(() -> provider.infer(frame())));
        assertThat(provider.loadStarted.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        releaseLoad.countDown();

        for (Future<FrameMetadata> inference : inferences) {
            assertThat(inference.get(WAIT_SECONDS, TimeUnit.SECONDS).tags()).containsExactly("seen");
        }
        assertThat(provider.loads).hasValue(1);
        provider.close();
    }
}
