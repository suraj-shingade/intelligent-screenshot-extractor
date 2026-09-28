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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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

    /** Serialises loading, so two threads never download the same weights. */
    private final Object loadLock = new Object();

    /**
     * Inference holds the read lock and may run concurrently; publishing a model
     * and closing the provider hold the write lock. Deliberately separate from
     * {@link #loadLock} so that closing never waits on a download.
     */
    private final ReadWriteLock lifecycle = new ReentrantReadWriteLock();

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
        if (ensureModel() == null) {
            return FrameMetadata.empty();
        }
        // Many job threads may predict at once; only close() excludes them.
        lifecycle.readLock().lock();
        try {
            // Re-read under the lock: close() may have run since ensureModel().
            ZooModel<Image, T> current = model;
            if (closed || current == null) {
                return FrameMetadata.empty();
            }
            Predictor<Image, T> predictor = predictorFor(current);
            Image image = ImageFactory.getInstance().fromImage(frame.image());
            T prediction = predictor.predict(image);
            FrameMetadata metadata = toMetadata(prediction);
            return metadata == null ? FrameMetadata.empty() : metadata;
        } catch (Exception ex) {
            log.warn("Model '{}' failed to analyse frame index={} -- recording no metadata",
                    name(), frame.index(), ex);
            return FrameMetadata.empty();
        } finally {
            lifecycle.readLock().unlock();
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
            ZooModel<Image, T> loaded;
            try {
                loaded = loadModel();
            } catch (Exception ex) {
                loadFailed = true;
                log.error("Could not load model for provider '{}'. AI analysis is disabled "
                        + "for the rest of this run; frame extraction continues unaffected.",
                        name(), ex);
                return null;
            }
            return publish(loaded);
        }
    }

    /**
     * Make a freshly loaded model visible to inference, unless the provider was
     * closed while it loaded.
     *
     * <p>A load can take minutes on first use, and shutdown must not wait for it.
     * So {@link #close()} never touches {@code loadLock}: it can run to completion
     * while a download is still in flight, see no model, and return. Without this
     * check the download would then finish and publish a model that nothing will
     * ever close. Publishing under the write lock means either close ran first and
     * the new model is discarded here, or publishing ran first and close sees the
     * model and releases it. Never neither.</p>
     */
    private ZooModel<Image, T> publish(ZooModel<Image, T> loaded) {
        lifecycle.writeLock().lock();
        try {
            if (!closed) {
                model = loaded;
                log.info("Model for provider '{}' ready", name());
                return loaded;
            }
        } finally {
            lifecycle.writeLock().unlock();
        }
        log.info("Provider '{}' was closed while its model loaded; discarding it", name());
        closeQuietly(loaded, "model");
        return null;
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

    /**
     * Release the model and every predictor. Safe to call more than once.
     *
     * <p>Waits for predictions already in progress to finish, because freeing a
     * model's native memory under a running prediction is a use-after-free in
     * native code. It does <em>not</em> wait for a model download in progress;
     * see {@link #publish} for how a model that finishes loading afterwards is
     * still released.</p>
     */
    @Override
    public final void close() {
        List<Predictor<Image, T>> predictors;
        ZooModel<Image, T> current;

        lifecycle.writeLock().lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            predictors = List.copyOf(livePredictors);
            livePredictors.clear();
            current = model;
            model = null;
        } finally {
            lifecycle.writeLock().unlock();
        }
        predictorPerThread.remove();

        // Nothing can be predicting now: in-flight work finished before the
        // write lock was granted, and new work sees `closed` under the read lock.
        for (Predictor<Image, T> predictor : predictors) {
            closeQuietly(predictor, "predictor");
        }
        if (current != null) {
            closeQuietly(current, "model");
        }
    }

    private void closeQuietly(AutoCloseable resource, String what) {
        try {
            resource.close();
        } catch (Exception ex) {
            log.debug("A {} for provider '{}' resisted closing", what, name(), ex);
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
