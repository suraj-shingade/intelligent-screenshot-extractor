/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.core.pipeline;

import com.srj.videotoimage.application.ExtractionJob;
import com.srj.videotoimage.application.ExtractionRequest;
import com.srj.videotoimage.core.model.Frame;
import com.srj.videotoimage.core.model.VideoSource;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineExecutorTest {

    private static PipelineContext ctx() {
        ExtractionRequest request = ExtractionRequest.builder()
                .source(new VideoSource(URI.create("file:///tmp/x.mp4"), "x.mp4"))
                .outputDirectory(Paths.get("/tmp/out"))
                .build();
        return new PipelineContext(new ExtractionJob(request));
    }

    private static Frame frame() {
        return new Frame(0L, Duration.ZERO, new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB));
    }

    @Test
    void continueThenTerminalRunsBothStages() throws Exception {
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();

        PipelineExecutor executor = new PipelineExecutor(List.of(
                stage("a", (f, c) -> { first.incrementAndGet(); return StageResult.CONTINUE; }),
                stage("b", (f, c) -> { second.incrementAndGet(); return StageResult.ACCEPT_TERMINAL; })
        ));

        StageResult result = executor.execute(frame(), ctx());

        assertThat(result).isEqualTo(StageResult.ACCEPT_TERMINAL);
        assertThat(first.get()).isEqualTo(1);
        assertThat(second.get()).isEqualTo(1);
    }

    @Test
    void rejectShortCircuitsChain() throws Exception {
        AtomicInteger second = new AtomicInteger();
        PipelineExecutor executor = new PipelineExecutor(List.of(
                stage("drop", (f, c) -> StageResult.REJECT),
                stage("never", (f, c) -> { second.incrementAndGet(); return StageResult.ACCEPT_TERMINAL; })
        ));

        StageResult result = executor.execute(frame(), ctx());

        assertThat(result).isEqualTo(StageResult.REJECT);
        assertThat(second.get()).isZero();
    }

    @Test
    void fallingOffEndIsTreatedAsAccepted() throws Exception {
        PipelineExecutor executor = new PipelineExecutor(List.of(
                stage("a", (f, c) -> StageResult.CONTINUE)
        ));

        assertThat(executor.execute(frame(), ctx())).isEqualTo(StageResult.ACCEPT_TERMINAL);
    }

    @Test
    void stageExceptionPropagates() {
        PipelineExecutor executor = new PipelineExecutor(List.of(
                stage("boom", (f, c) -> { throw new IllegalStateException("nope"); })
        ));

        assertThat(Boolean.TRUE).isTrue();
        try {
            executor.execute(frame(), ctx());
            assertThat(false).as("expected exception").isTrue();
        } catch (Exception ex) {
            assertThat(ex).isInstanceOf(IllegalStateException.class);
        }
    }

    private interface StageBody {
        StageResult apply(Frame frame, PipelineContext ctx) throws Exception;
    }

    private static FrameStage stage(String name, StageBody body) {
        return new FrameStage() {
            @Override public String name() { return name; }
            @Override public StageResult process(Frame frame, PipelineContext ctx) throws Exception {
                return body.apply(frame, ctx);
            }
        };
    }
}
