package dev.hatek.client.feature.voice;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Голос Джарвиса: синтез через Windows SAPI (предпочитает русский голос),
 * воспроизведение на выбранном динамике. Пока говорит — микрофон оглушён,
 * чтобы не слышать самого себя.
 */
public final class JarvisSpeech {
    private static final String SCRIPT = String.join("\n",
            "param([string]$TextFile, [string]$WavFile)",
            "Add-Type -AssemblyName System.Speech",
            "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer",
            "try {",
            "  $ru = @($s.GetInstalledVoices() | Where-Object { $_.Enabled -and $_.VoiceInfo.Culture.Name -like 'ru*' })",
            "  if ($ru.Count -gt 0) { $s.SelectVoice($ru[0].VoiceInfo.Name) }",
            "  $s.Rate = 1",
            "  $s.SetOutputToWaveFile($WavFile)",
            "  $text = [IO.File]::ReadAllText($TextFile, [Text.Encoding]::UTF8)",
            "  $s.Speak($text)",
            "} finally {",
            "  $s.Dispose()",
            "}");

    private record Job(String text, String speaker) {
    }

    private static final LinkedBlockingQueue<Job> QUEUE = new LinkedBlockingQueue<>();
    private static volatile Thread worker;
    private static volatile boolean speaking;

    private static volatile Boolean available;

    private JarvisSpeech() {
    }

    public static boolean speaking() {
        return speaking;
    }

    public static boolean available() {
        Boolean cached = available;
        if (cached != null) {
            return cached;
        }
        boolean ok = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                && powershellWorks();
        available = ok;
        return ok;
    }

    private static boolean powershellWorks() {
        try {
            Process process = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    "$PSVersionTable.PSVersion.Major").redirectErrorStream(true).start();
            boolean done = process.waitFor(8, TimeUnit.SECONDS);
            if (!done) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Устройства вывода (динамики) с поддержкой воспроизведения. */
    public static List<String> playbackDevices() {
        List<String> names = new ArrayList<>();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(info);
                if (mixer.getSourceLineInfo().length > 0) {
                    names.add(info.getName());
                }
            } catch (Throwable ignored) {
            }
        }
        return names;
    }

    public static void speak(String text, String speaker) {
        if (text == null || text.isBlank() || !available()) {
            return;
        }
        if (QUEUE.size() > 2) {
            QUEUE.clear();
        }
        QUEUE.offer(new Job(text.strip(), speaker == null ? "" : speaker.strip()));
        ensureWorker();
    }

    public static void stop() {
        QUEUE.clear();
    }

    private static synchronized void ensureWorker() {
        Thread current = worker;
        if (current != null && current.isAlive()) {
            return;
        }
        Thread thread = new Thread(JarvisSpeech::loop, "Hatek Jarvis Voice");
        thread.setDaemon(true);
        worker = thread;
        thread.start();
    }

    private static void loop() {
        try {
            Path dir = Files.createDirectories(
                    Path.of(System.getProperty("java.io.tmpdir"), "hatek-tts"));
            Path script = dir.resolve("speak.ps1");
            Path textFile = dir.resolve("phrase.txt");
            Path wavFile = dir.resolve("out.wav");
            if (!Files.isRegularFile(script)) {
                Files.writeString(script, SCRIPT, StandardCharsets.UTF_8);
            }
            while (true) {
                Job job = QUEUE.take();
                say(job, script, textFile, wavFile);
            }
        } catch (InterruptedException ignored) {
        } catch (Throwable ignored) {
        }
    }

    private static void say(Job job, Path script, Path textFile, Path wavFile) {
        VoiceEngine.deafened(true);
        try {
            Files.writeString(textFile, job.text(), StandardCharsets.UTF_8);
            Files.deleteIfExists(wavFile);
            Process process = new ProcessBuilder("powershell", "-NoProfile",
                    "-ExecutionPolicy", "Bypass", "-File", script.toString(),
                    "-TextFile", textFile.toString(), "-WavFile", wavFile.toString())
                    .redirectErrorStream(true).start();
            boolean done = process.waitFor(20, TimeUnit.SECONDS);
            if (!done) {
                process.destroyForcibly();
                return;
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(wavFile)) {
                return;
            }
            play(wavFile, job.speaker());
        } catch (Throwable ignored) {
        } finally {
            VoiceEngine.deafened(false);
        }
    }

    private static void play(Path wavFile, String speaker) throws Exception {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(wavFile.toFile())) {
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    16_000.0F, 16, 1, 2, 16_000.0F, false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(target, in)) {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, target);
                SourceDataLine line = openLine(speaker, info);
                line.open(target);
                line.start();
                speaking = true;
                try {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = pcm.read(buffer)) > 0) {
                        line.write(buffer, 0, read);
                    }
                    line.drain();
                } finally {
                    speaking = false;
                    line.stop();
                    line.close();
                }
            }
        }
    }

    private static SourceDataLine openLine(String speaker, DataLine.Info info) throws Exception {
        if (!speaker.isEmpty()) {
            for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
                try {
                    if (!mixerInfo.getName().equals(speaker)) {
                        continue;
                    }
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    if (mixer.isLineSupported(info)) {
                        return (SourceDataLine) mixer.getLine(info);
                    }
                } catch (Throwable ignored) {
                }
            }
            for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
                try {
                    if (!mixerInfo.getName().contains(speaker)) {
                        continue;
                    }
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    if (mixer.isLineSupported(info)) {
                        return (SourceDataLine) mixer.getLine(info);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return (SourceDataLine) AudioSystem.getLine(info);
    }
}
