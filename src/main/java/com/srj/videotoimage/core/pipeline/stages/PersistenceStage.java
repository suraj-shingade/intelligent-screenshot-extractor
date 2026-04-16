/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.pipeline.stages;

import com.srj.videotoimage.application.ExtractionRequest;
import com.srj.videotoimage.core.model.Frame;
import com.srj.videotoimage.core.pipeline.FrameStage;
import com.srj.videotoimage.core.pipeline.PipelineContext;
import com.srj.videotoimage.core.pipeline.StageResult;
import com.srj.videotoimage.exception.VideoToImageException;
import com.srj.videotoimage.infrastructure.persistence.FrameWriter;
import com.srj.videotoimage.infrastructure.persistence.MetadataWriter;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Writes the accepted frame (and optional metadata sidecar) to disk. Always
 * terminal: returning {@link StageResult#ACCEPT_TERMINAL} halts the chain.
 *
 * @author Suraj Shingade
 */
public final class PersistenceStage implements FrameStage {

    public static final String LAST_WRITTEN_PATH_ATTR = "persistence.lastPath";

    private final FrameWriter frameWriter;
    private final MetadataWriter metadataWriter;

    public PersistenceStage(FrameWriter frameWriter, MetadataWriter metadataWriter) {
        this.frameWriter = Objects.requireNonNull(frameWriter, "frameWriter");
        this.metadataWriter = Objects.requireNonNull(metadataWriter, "metadataWriter");
    }

    @Override
    public String name() {
        return "persistence";
    }

    @Override
    public StageResult process(Frame frame, PipelineContext context) throws VideoToImageException {
        ExtractionRequest req = context.request();
        long ordinal = context.nextAcceptedFrameOrdinal();
        Path imagePath = frameWriter.write(frame, ordinal, req);
        if (req.writeMetadataSidecar() && frame.metadata() != null) {
            metadataWriter.write(imagePath, frame.metadata());
        }
        context.setAttribute(LAST_WRITTEN_PATH_ATTR, imagePath);
        return StageResult.ACCEPT_TERMINAL;
    }
}
