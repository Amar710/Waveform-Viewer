import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.util.PriorityQueue;
import java.util.HashMap;
import java.util.Map;

public class WaveformViewer {

    private JFrame frame;
    private WaveformPanel waveformPanel;

    public WaveformViewer() {
        frame = new JFrame("Waveform Viewer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 500);

        waveformPanel = new WaveformPanel();
        frame.add(waveformPanel, BorderLayout.CENTER);

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem openMenuItem = new JMenuItem("Open");
        openMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                FileNameExtensionFilter filter = new FileNameExtensionFilter("WAV Files", "wav");
                fileChooser.setFileFilter(filter);

                int returnValue = fileChooser.showOpenDialog(frame);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = fileChooser.getSelectedFile();
                    if (selectedFile.getName().toLowerCase().endsWith(".wav")) {
                        try {
                            loadWaveform(selectedFile);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    } else {
                        JOptionPane.showMessageDialog(frame, "Selected file is not a .WAV file!", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });
        fileMenu.add(openMenuItem);
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);
        frame.setVisible(true);
    }

    private void loadWaveform(File file) throws Exception {
        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(file);
        AudioFormat format = audioInputStream.getFormat();

        if (format.getChannels() != 2) {
            throw new Exception("Only stereo files are supported.");
        }

        byte[] audioBytes = new byte[(int) audioInputStream.getFrameLength() * format.getFrameSize()];
        audioInputStream.read(audioBytes);

        int[] leftChannel = new int[audioBytes.length / 4];
        int[] rightChannel = new int[audioBytes.length / 4];

        int sampleIndex = 0;
        for (int i = 0; i < audioBytes.length; i += 4) {
            leftChannel[sampleIndex] = (audioBytes[i + 1] << 8) + (audioBytes[i] & 0xff);
            rightChannel[sampleIndex] = (audioBytes[i + 3] << 8) + (audioBytes[i + 2] & 0xff);
            sampleIndex++;
        }
        // Combine the left and right channels into one array for entropy calculation
        int[] combinedSamples = new int[leftChannel.length + rightChannel.length];
        for (int i = 0; i < leftChannel.length; i++) {
            combinedSamples[2 * i] = leftChannel[i];
            combinedSamples[2 * i + 1] = rightChannel[i];
        }

        Map<Integer, String> huffmanCodes = generateHuffmanCodes(combinedSamples); // As mentioned, just using leftChannel for simplicity
        double entropy = computeEntropy(combinedSamples);
        double avgCodeLength = computeAverageCodeLength(combinedSamples, huffmanCodes);

        waveformPanel.setWaveforms(leftChannel, rightChannel, (int) format.getSampleRate(), entropy, avgCodeLength);
    }

    // Huffman Tree Node
    class HuffmanNode implements Comparable<HuffmanNode> {
    int data;
    int frequency;
    HuffmanNode left;
    HuffmanNode right;

    @Override
    public int compareTo(HuffmanNode o) {
        return this.frequency - o.frequency;
    }
    }

    // Generates Huffman codes for the given audio samples.
    private Map<Integer, String> generateHuffmanCodes(int[] samples) {
    Map<Integer, Integer> freqMap = new HashMap<>();
    for (int sample : samples) {
        freqMap.put(sample, freqMap.getOrDefault(sample, 0) + 1);
    }

    PriorityQueue<HuffmanNode> queue = new PriorityQueue<>();

    for (Map.Entry<Integer, Integer> entry : freqMap.entrySet()) {
        HuffmanNode node = new HuffmanNode();
        node.data = entry.getKey();
        node.frequency = entry.getValue();
        queue.add(node);
    }

    while (queue.size() > 1) {
        HuffmanNode x = queue.poll();
        HuffmanNode y = queue.poll();

        HuffmanNode combined = new HuffmanNode();
        combined.frequency = x.frequency + y.frequency;
        combined.left = x;
        combined.right = y;

        queue.add(combined);
    }

    Map<Integer, String> huffmanCodes = new HashMap<>();
    generateCodesRecursive(queue.peek(), "", huffmanCodes);

    return huffmanCodes;
}

private void generateCodesRecursive(HuffmanNode root, String code, Map<Integer, String> huffmanCodes) {
    if (root.left == null && root.right == null) {
        huffmanCodes.put(root.data, code);
        return;
    }

    if (root.left != null) {
        generateCodesRecursive(root.left, code + "0", huffmanCodes);
    }

    if (root.right != null) {
        generateCodesRecursive(root.right, code + "1", huffmanCodes);
    }
}

// Math function to compute the Entropy
private double computeEntropy(int[] samples) {
    Map<Integer, Integer> freqMap = new HashMap<>();
    double totalSamples = samples.length;

    for (int sample : samples) {
        freqMap.put(sample, freqMap.getOrDefault(sample, 0) + 1);
    }

    double entropy = 0;
    for (Integer freq : freqMap.values()) {
        double prob = freq / totalSamples;
        entropy += prob * (Math.log(prob) / Math.log(2));
    }

    return -entropy;
}

// Math function to caompute the Average Code Length
private double computeAverageCodeLength(int[] samples, Map<Integer, String> huffmanCodes) {
    Map<Integer, Integer> freqMap = new HashMap<>();
    double totalSamples = samples.length;

    for (int sample : samples) {
        freqMap.put(sample, freqMap.getOrDefault(sample, 0) + 1);
    }

    double avgLength = 0;
    for (Map.Entry<Integer, Integer> entry : freqMap.entrySet()) {
        avgLength += (entry.getValue() / totalSamples) * huffmanCodes.get(entry.getKey()).length();
    }

    return avgLength;
}

    private class WaveformPanel extends JPanel {
        private int[] leftChannel;
        private int[] rightChannel;
        private int sampleRate;
        private double entropy;
        private double avgCodeLength;

        public void setWaveforms(int[] leftChannel, int[] rightChannel, int sampleRate, double entropy, double avgCodeLength) {
            this.leftChannel = leftChannel;
            this.rightChannel = rightChannel;
            this.sampleRate = sampleRate;
            this.entropy = entropy;
            this.avgCodeLength = avgCodeLength;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (leftChannel == null || rightChannel == null) return;

            int width = getWidth();
            int height = getHeight();
            int centerY = height / 2;
            int gap = height / 4;
            int leftChannelY = centerY - gap;
            int rightChannelY = centerY + gap;

            for (int i = 0; i < leftChannel.length; i++) {
                int x = (int) (((double) i / leftChannel.length) * width);
                int leftY = (int) (leftChannelY - (leftChannel[i] / 65536.0) * gap);
                int rightY = (int) (rightChannelY - (rightChannel[i] / 65536.0) * gap);
                g.drawLine(x, leftChannelY, x, leftY);
                g.drawLine(x, rightChannelY, x, rightY);
            }
            // Format Values
            String formattedEntropy = String.format("%.4f", entropy);
            String formattedavgCodeLength = String.format("%.4f", avgCodeLength);
            g.drawString("Sample Rate: " + sampleRate + " Hz", 10, 20);
            g.drawString("Total Samples: " + leftChannel.length, 10, 40);
            g.drawString("Entropy: " + formattedEntropy, 10, 60);
            g.drawString("Average Code Word Length: " + formattedavgCodeLength, 10, 80);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new WaveformViewer());
    }
}
