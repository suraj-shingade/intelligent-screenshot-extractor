/*
 * Intelligent Screenshot Extractor
 * Copyright (c) 2026 Suraj Shingade
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 * https://github.com/suraj-shingade/intelligent-screenshot-extractor
 */

package com.srj.videotoimage.ui.panels;

import com.srj.videotoimage.application.ExtractionRequest;
import com.srj.videotoimage.config.AppConfig;
import com.srj.videotoimage.core.ai.FrameAnalyzer;
import com.srj.videotoimage.core.model.OutputFormat;
import com.srj.videotoimage.core.model.UniquenessPreset;
import com.srj.videotoimage.core.sampling.SamplingStrategy;
import com.srj.videotoimage.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.Dimension;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Composite panel for the extraction configuration form. Produces
 * {@link ExtractionRequest} instances on demand for jobs the user enqueues.
 *
 * @author Suraj Shingade
 */
public final class ConfigPanel extends JPanel {

    private final AppConfig config;

    private final JTextField outputDir = new JTextField(28);
    private final JComboBox<SamplingStrategy> samplingBox = new JComboBox<>(SamplingStrategy.values());
    private final JSpinner intervalFrames;
    private final JSpinner intervalSeconds;
    private final JComboBox<OutputFormat> formatBox = new JComboBox<>(OutputFormat.values());
    private final JSpinner jpegQuality;
    private final JSpinner maxWidth;
    private final JSpinner maxHeight;
    private final JSlider uniquenessSlider = new JSlider(0, 2);
    private final JSpinner maxFrames;
    private final JList<String> analyzerList = new JList<>();

    public ConfigPanel(AppConfig config, Set<FrameAnalyzer> analyzers) {
        super(new MigLayout("insets 8, wrap 2, fillx", "[right][grow, fill]"));
        this.config = config;
        setBorder(BorderFactory.createTitledBorder(Messages.get("config.title")));

        outputDir.setText(config.defaultOutputDirectory().toString());
        samplingBox.setSelectedItem(config.defaultSamplingStrategy());
        intervalFrames = new JSpinner(new SpinnerNumberModel(config.defaultIntervalFrames(), 1, 10_000, 1));
        intervalSeconds = new JSpinner(new SpinnerNumberModel(
                config.defaultIntervalSeconds().toMillis() / 1000d, 0.01d, 3600d, 0.1d));
        formatBox.setSelectedItem(config.defaultOutputFormat());
        jpegQuality = new JSpinner(new SpinnerNumberModel((double) config.defaultJpegQuality(), 0d, 1d, 0.05d));
        maxWidth = new JSpinner(new SpinnerNumberModel(config.defaultMaxWidth(), 32, 16_384, 16));
        maxHeight = new JSpinner(new SpinnerNumberModel(config.defaultMaxHeight(), 32, 16_384, 16));
        uniquenessSlider.setPaintTicks(true);
        uniquenessSlider.setPaintLabels(true);
        uniquenessSlider.setMajorTickSpacing(1);
        uniquenessSlider.setValue(config.defaultUniquenessPreset().ordinal());
        maxFrames = new JSpinner(new SpinnerNumberModel(config.maxFramesPerJob(), 0, 1_000_000, 10));

        // Output directory
        add(new JLabel(Messages.get("config.output.folder")));
        JPanel dirRow = new JPanel(new MigLayout("insets 0, fillx", "[grow, fill][]"));
        dirRow.add(outputDir, "grow");
        JButton browse = new JButton(Messages.get("config.output.browse"));
        browse.addActionListener(e -> pickDirectory());
        dirRow.add(browse);
        add(dirRow, "grow");

        // Sampling
        add(new JLabel(Messages.get("config.sampling.strategy")));
        add(samplingBox, "growx");
        add(new JLabel(Messages.get("config.sampling.intervalFrames")));
        add(intervalFrames, "growx");
        add(new JLabel(Messages.get("config.sampling.intervalSeconds")));
        add(intervalSeconds, "growx");

        // Format
        add(new JLabel(Messages.get("config.output.format")));
        add(formatBox, "growx");
        add(new JLabel(Messages.get("config.output.jpegQuality")));
        add(jpegQuality, "growx");

        // Resize
        add(new JLabel(Messages.get("config.output.maxWidth")));
        add(maxWidth, "growx");
        add(new JLabel(Messages.get("config.output.maxHeight")));
        add(maxHeight, "growx");

        // Uniqueness
        add(new JLabel(Messages.get("config.uniqueness.title")));
        var labels = new java.util.Hashtable<Integer, JLabel>();
        labels.put(0, new JLabel(Messages.get("config.uniqueness.strict")));
        labels.put(1, new JLabel(Messages.get("config.uniqueness.balanced")));
        labels.put(2, new JLabel(Messages.get("config.uniqueness.permissive")));
        uniquenessSlider.setLabelTable(labels);
        add(uniquenessSlider, "growx");

        // Max frames
        add(new JLabel(Messages.get("config.maxFrames")));
        add(maxFrames, "growx");

        // Analyzers
        add(new JLabel(Messages.get("config.ai.title")));
        DefaultListModel<String> listModel = new DefaultListModel<>();
        if (analyzers == null || analyzers.isEmpty()) {
            listModel.addElement(Messages.get("config.ai.noneDiscovered"));
        } else {
            for (FrameAnalyzer a : analyzers) {
                listModel.addElement(a.name() + (a.isEnabled() ? "" : "  (disabled)"));
            }
        }
        analyzerList.setModel(listModel);
        JScrollPane scroll = new JScrollPane(analyzerList);
        scroll.setPreferredSize(new Dimension(0, 80));
        add(scroll, "grow");
    }

    private void pickDirectory() {
        JFileChooser chooser = new JFileChooser(outputDir.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            outputDir.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    public ExtractionRequest.Builder toBuilder() {
        double seconds = (Double) intervalSeconds.getValue();
        Duration intervalDur = Duration.ofMillis((long) (seconds * 1000));
        UniquenessPreset preset = UniquenessPreset.values()[uniquenessSlider.getValue()];

        return ExtractionRequest.builder()
                .outputDirectory(Paths.get(outputDir.getText()))
                .outputFormat((OutputFormat) formatBox.getSelectedItem())
                .jpegQuality(((Double) jpegQuality.getValue()).floatValue())
                .samplingStrategy((SamplingStrategy) samplingBox.getSelectedItem())
                .intervalFrames((Integer) intervalFrames.getValue())
                .intervalSeconds(intervalDur)
                .resize(config.defaultResizeEnabled(),
                        (Integer) maxWidth.getValue(),
                        (Integer) maxHeight.getValue())
                .uniquenessPreset(preset)
                .uniquenessWindowSize(config.uniquenessWindowSize())
                .maxFramesPerJob((Integer) maxFrames.getValue())
                .writeMetadataSidecar(config.writeMetadataSidecar())
                .enabledAnalyzerNames(List.of());
    }

    public Path currentOutputDirectory() {
        return Paths.get(outputDir.getText());
    }

    @SuppressWarnings("unused")
    private static List<String> toList(List<?> in) {
        List<String> out = new ArrayList<>(in.size());
        for (Object o : in) out.add(o.toString());
        return out;
    }
}
