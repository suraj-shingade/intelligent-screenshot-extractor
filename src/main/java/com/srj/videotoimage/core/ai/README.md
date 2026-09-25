# AI Extension Guide

This package holds the pluggable AI pipeline. Two working models ship with the
application, and the extension points below let you add your own without
touching core pipeline code.

AI analysis is **off by default**. Enabling it downloads model weights on the
first analysed frame, so it stays an explicit choice rather than a surprise on
someone's connection. With it off, the pipeline carries zero AI overhead.

## What ships

| Provider name | Class | Output |
|---|---|---|
| `resnet` | `ImageClassificationModelProvider` | Labels for the frame, written as `tags`, with confidences under `analyzerData.resnet` |
| `ssd` | `ObjectDetectionModelProvider` | One `DetectedObject` per detection: label, confidence, and a bounding box normalised to `[0,1]` |
| `placeholder` | `PlaceholderModelProvider` | Nothing. Loads no model; useful for exercising the wiring offline |

Turn one on in `application.conf`:

```hocon
ai.djl {
    enabled       = true
    modelProvider = "resnet"   # or "ssd"
    minConfidence = 0.25       # discard weaker predictions
    maxResults    = 5          # most labels or objects per frame
}
```

## Extension points

1. **`FrameAnalyzer`** (`com.srj.videotoimage.core.ai.FrameAnalyzer`)
   The top-level SPI. One instance per analysis capability (object detection,
   classification, captioning, embeddings, ...). Register in
   `META-INF/services/com.srj.videotoimage.core.ai.FrameAnalyzer`.

2. **`ModelProvider`** (`com.srj.videotoimage.core.ai.djl.ModelProvider`)
   A narrower extension point used by the shipped `DjlFrameAnalyzer`. Use this
   when you want the existing lifecycle, configuration and SPI wiring and only
   need to supply a model. Register in
   `META-INF/services/com.srj.videotoimage.core.ai.djl.ModelProvider`.

## Adding a DJL model (recommended path)

Extend `AbstractZooModelProvider`. It already handles the four things that are
easy to get wrong when hosting a neural network inside a desktop app:

- **Lazy loading.** Weights are fetched on the first frame actually analysed,
  never at startup.
- **Latched failure.** A model that cannot be loaded is reported once, then
  skipped. Without this, a failing download would be retried per frame.
- **Per-thread predictors.** A DJL `Predictor` is not thread-safe, but one model
  serves many job threads. Each thread gets its own predictor over the shared
  model, and all of them are closed on shutdown.
- **Inference never fails a job.** An exception from one prediction is logged and
  downgraded to empty metadata. Frame extraction is the product; analysis is a
  bonus on top of it.

So a new provider only supplies a `Criteria` and a mapping to `FrameMetadata`:

```java
public final class SegmentationModelProvider
        extends AbstractZooModelProvider<CategoryMask> {

    public SegmentationModelProvider() {                 // required by ServiceLoader
        this(ConfigFactory.load());
    }

    public SegmentationModelProvider(Config config) {
        super(DjlProviderSettings.from(config).maxResults(),
              DjlProviderSettings.from(config).minConfidence());
    }

    @Override
    public String name() {
        return "segmentation";
    }

    @Override
    protected Criteria<Image, CategoryMask> criteria() {
        return Criteria.builder()
                .optApplication(Application.CV.SEMANTIC_SEGMENTATION)
                .setTypes(Image.class, CategoryMask.class)
                .optEngine("PyTorch")
                .build();
    }

    @Override
    protected FrameMetadata toMetadata(CategoryMask prediction) {
        return new FrameMetadata(labelsOf(prediction), List.of(), List.of(), Map.of());
    }
}
```

Register it:

```
# META-INF/services/com.srj.videotoimage.core.ai.djl.ModelProvider
com.example.SegmentationModelProvider
```

And select it:

```hocon
ai.djl {
    enabled       = true
    modelProvider = "segmentation"
}
```

Providers are resolved by name through `ServiceLoader`, so an unknown name falls
back to `placeholder` with a warning rather than failing startup.

## Adding a non-DJL analyzer

Implement `FrameAnalyzer` directly (for example calling a hosted vision API, or
running OpenCV DNN locally) and register it via SPI. The pipeline's
`AnalysisStage` picks it up automatically through Guice's
`Multibinder<FrameAnalyzer>`.

Two things to honour:

- **Be thread-safe.** A single instance may be invoked concurrently from several
  job threads.
- **Report `isEnabled()` honestly.** The stage skips disabled analyzers without
  calling them, and the UI shows the flag in the AI Analyzers list.

If your analyzer holds resources, implement `AutoCloseable` as well.
`VideoToImageApplication` closes every analyzer that does, on shutdown.

## Which analyzers run

`AnalysisStage` runs the intersection of what is enabled and what the job asked
for. The **AI Analyzers** list on the configuration panel is multi-select and
feeds `ExtractionRequest.enabledAnalyzerNames()`; analyzers already enabled in
configuration start selected. An empty selection means "no filter", so whatever
is enabled runs.
