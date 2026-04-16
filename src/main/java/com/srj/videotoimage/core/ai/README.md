# AI Extension Guide

This package defines the pluggable AI pipeline for VideoToImage. Everything
here is intentionally stubbed-out at ship time -- the application runs with
zero AI overhead by default. This document explains how to add real AI
capabilities without modifying core pipeline code.

## Extension points

1. **`FrameAnalyzer`** (`com.srj.videotoimage.core.ai.FrameAnalyzer`)
   The top-level SPI. One instance per analysis capability (object detection,
   classification, captioning, embeddings, ...). Register in
   `META-INF/services/com.srj.videotoimage.core.ai.FrameAnalyzer`.

2. **`ModelProvider`** (`com.srj.videotoimage.core.ai.djl.ModelProvider`)
   A narrower extension point used by the shipped `DjlFrameAnalyzer` to load
   a DJL `ZooModel` and run inference. Use this when you want to leverage the
   existing DJL wiring (lifecycle, configuration, SPI discovery) and only
   need to supply a model. Register in
   `META-INF/services/com.srj.videotoimage.core.ai.djl.ModelProvider`.

## Adding a DJL model (recommended path)

```java
public final class ResNet50ModelProvider implements ModelProvider {

    private final ZooModel<Image, Classifications> model;
    private final Predictor<Image, Classifications> predictor;

    public ResNet50ModelProvider() {
        Criteria<Image, Classifications> criteria = Criteria.builder()
                .setTypes(Image.class, Classifications.class)
                .optApplication(Application.CV.IMAGE_CLASSIFICATION)
                .optArtifactId("resnet")
                .optFilter("flavor", "v1")
                .optFilter("dataset", "imagenet")
                .build();
        this.model = criteria.loadModel();
        this.predictor = model.newPredictor();
    }

    @Override public String name() { return "resnet50"; }

    @Override public FrameMetadata infer(Frame frame) { /* ... */ }

    @Override public void close() { predictor.close(); model.close(); }
}
```

Enable it in `application.conf`:

```hocon
ai.djl {
    enabled       = true
    modelProvider = "resnet50"
}
```

## Adding a non-DJL analyzer

Implement `FrameAnalyzer` directly (e.g. calling OpenAI Vision, AWS
Rekognition, or running OpenCV DNN locally) and register via SPI. The
pipeline's `AnalysisStage` will automatically pick it up.
