/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.ai.djl;

import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import com.srj.videotoimage.core.model.Frame;
import com.srj.videotoimage.core.model.FrameMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared plumbing for {@link ModelProvider}s backed by a DJL model zoo entry.
 *
 * <p>Subclasses supply two things: the {@link Criteria} naming the model, and a
 * mapping from the model's output type to {@link FrameMetadata}. Everything
 * awkward about hosting a neural network inside a desktop app is handled here:</p>
 *
 * <ul>
 *   <li><strong>Lazy loading.</strong> Weights are fetched on the first frame
 *       actually analysed, not at startup, so enabling AI never delays the
 *       window opening and a user who never runs a job never pays the download.</li>
 *   <li><strong>One failure, not thousands.</strong> If the model cannot be
 *       loaded -- no network, no engine native libraries, an unavailable
 *       artifact -- the failure is logged once and latched. Later frames return
 *       empty metadata immediately rather than retrying a large download per
 *       frame.</li>
 *   <li><strong>Per-thread predictors.</strong> A DJL {@code Predictor} is not
 *       thread-safe, but one model instance serves many job threads. Each
 *       thread gets its own predictor over the shared model, and all of them
 *       are tracked so {@link #close()} can release the native memory.</li>
 *   <li><strong>Inference never fails a job.</strong> Any exception from a
 *       single prediction is logged and downgraded to empty metadata. Frame
 *       extraction is the product; analysis is a bonus on top of it.</li>
 * </ul>
 *
 * @param <T> the model's output type, e.g. {@code Classifications}
 *
 * @author Suraj Shingade
 */
public abstract class AbstractZooModelProvider<T> implements ModelProvider {

    private static final Logger log = LoggerFactory.getLogger(AbstractZooModelProvider.class);

    private final int maxResults;
    private final double minConfidence;

    private final Object loadLock = new Object();
    private final List<Predictor<Image, T>> livePredictors = new CopyOnWriteArrayList<>();
    private final ThreadLocal<Predictor<Image, T>> predictorPerThread = new ThreadLocal<>();

    private volatile ZooModel<Image, T> model;
    private volatile boolean loadFailed;
    private volatile boolean closed;

    /**
     * @param maxResults    most labels or objects to record per frame
     * @param minConfidence predictions weaker than this are discarded
     */
    protected AbstractZooModelProvider(int maxResults, double minConfidence) {
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be > 0");
        }
        if (minConfidence < 0d || minConfidence > 1d) {
            throw new IllegalArgumentException("minConfidence must be in [0,1]");
        }
        this.maxResults = maxResults;
        this.minConfidence = minConfidence;
    }

    /** The model-zoo query identifying which weights to load. */
    protected abstract Criteria<Image, T> criteria();

    /** Translate one prediction into metadata to merge onto the frame. */
    protected abstract FrameMetadata toMetadata(T prediction);

    protected final int maxResults() {
        return maxResults;
    }

    protected final double minConfidence() {
        return minConfidence;
    }

    /**
     * Resolve and load the model. Overridable so tests can exercise the
     * lazy-load and failure-latch behaviour without touching the network.
     */
    protected ZooModel<Image, T> loadModel() throws Exception {
        return criteria().loadModel();
    }

    @Override
    public final FrameMetadata infer(Frame frame) {
        if (closed || loadFailed || frame == null) {
            return FrameMetadata.empty();
        }
        ZooModel<Image, T> loaded = ensureModel();
        if (loaded == null) {
            return FrameMetadata.empty();
        }
        try {
            Predictor<Image, T> predictor = predictorFor(loaded);
            Image image = ImageFactory.getInstance().fromImage(frame.image());
            T prediction = predictor.predict(image);
            FrameMetadata metadata = toMetadata(prediction);
            return metadata == null ? FrameMetadata.empty() : metadata;
        } catch (Exception ex) {
            log.warn("Model '{}' failed to analyse frame index={} -- recording no metadata",
                    name(), frame.index(), ex);
            return FrameMetadata.empty();
        }
    }

    private ZooModel<Image, T> ensureModel() {
        ZooModel<Image, T> current = model;
        if (current != null) {
            return current;
        }
        synchronized (loadLock) {
            if (model != null) {
                return model;
            }
            if (loadFailed || closed) {
                return null;
            }
            log.info("Loading model for provider '{}'. First use may download weights.", name());
            try {
                model = loadModel();
                log.info("Model for provider '{}' ready", name());
                return model;
            } catch (Exception ex) {
                loadFailed = true;
                log.error("Could not load model for provider '{}'. AI analysis is disabled "
                        + "for the rest of this run; frame extraction continues unaffected.",
                        name(), ex);
                return null;
            }
        }
    }

    private Predictor<Image, T> predictorFor(ZooModel<Image, T> loaded) {
        Predictor<Image, T> predictor = predictorPerThread.get();
        if (predictor == null) {
            predictor = loaded.newPredictor();
            predictorPerThread.set(predictor);
            livePredictors.add(predictor);
        }
        return predictor;
    }

    @Override
    public final void close() {
        closed = true;
        for (Predictor<Image, T> predictor : livePredictors) {
            try {
                predictor.close();
            } catch (RuntimeException ex) {
                log.debug("Predictor for provider '{}' resisted closing", name(), ex);
            }
        }
        livePredictors.clear();
        predictorPerThread.remove();

        ZooModel<Image, T> current = model;
        model = null;
        if (current != null) {
            try {
                current.close();
            } catch (RuntimeException ex) {
                log.debug("Model for provider '{}' resisted closing", name(), ex);
            }
        }
    }

    /** True once a load attempt has failed and been latched off. */
    public final boolean loadFailed() {
        return loadFailed;
    }

    /** True when a model is loaded and ready to serve predictions. */
    public final boolean loaded() {
        return model != null;
    }
}
