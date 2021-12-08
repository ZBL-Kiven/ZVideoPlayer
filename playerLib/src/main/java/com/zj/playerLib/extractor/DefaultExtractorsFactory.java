package com.zj.playerLib.extractor;

import com.zj.playerLib.extractor.amr.AmrExtractor;
import com.zj.playerLib.extractor.flv.FlvExtractor;
import com.zj.playerLib.extractor.mkv.MatroskaExtractor;
import com.zj.playerLib.extractor.mp3.Mp3Extractor;
import com.zj.playerLib.extractor.mp4.FragmentedMp4Extractor;
import com.zj.playerLib.extractor.mp4.Mp4Extractor;
import com.zj.playerLib.extractor.ogg.OggExtractor;
import com.zj.playerLib.extractor.ts.Ac3Extractor;
import com.zj.playerLib.extractor.ts.AdtsExtractor;
import com.zj.playerLib.extractor.ts.PsExtractor;
import com.zj.playerLib.extractor.ts.TsExtractor;
import com.zj.playerLib.extractor.wav.WavExtractor;

import java.lang.reflect.Constructor;

public final class DefaultExtractorsFactory implements ExtractorsFactory {
    private static final Constructor<? extends Extractor> FLAC_EXTRACTOR_CONSTRUCTOR;
    private boolean constantBitrateSeekingEnabled;
    private int adtsFlags;
    private int amrFlags;
    private int matroskaFlags;
    private int mp4Flags;
    private int fragmentedMp4Flags;
    private int mp3Flags;
    private int tsMode = 1;
    private int tsFlags;

    public DefaultExtractorsFactory() {
    }

    public synchronized DefaultExtractorsFactory setConstantBitrateSeekingEnabled(boolean constantBitrateSeekingEnabled) {
        this.constantBitrateSeekingEnabled = constantBitrateSeekingEnabled;
        return this;
    }

    public synchronized DefaultExtractorsFactory setAdtsExtractorFlags(int flags) {
        this.adtsFlags = flags;
        return this;
    }

    public synchronized DefaultExtractorsFactory setAmrExtractorFlags(int flags) {
        this.amrFlags = flags;
        return this;
    }

    public synchronized DefaultExtractorsFactory setMatroskaExtractorFlags(int flags) {
        this.matroskaFlags = flags;
        return this;
    }

    public synchronized DefaultExtractorsFactory setMp4ExtractorFlags(int flags) {
        this.mp4Flags = flags;
        return this;
    }

    public synchronized DefaultExtractorsFactory setFragmentedMp4ExtractorFlags(int flags) {
        this.fragmentedMp4Flags = flags;
        return this;
    }

    public synchronized DefaultExtractorsFactory setMp3ExtractorFlags(int flags) {
        this.mp3Flags = flags;
        return this;
    }

    public synchronized DefaultExtractorsFactory setTsExtractorMode(int mode) {
        this.tsMode = mode;
        return this;
    }

    public synchronized DefaultExtractorsFactory setTsExtractorFlags(int flags) {
        this.tsFlags = flags;
        return this;
    }

    public synchronized Extractor[] createExtractors() {
        Extractor[] extractors = new Extractor[FLAC_EXTRACTOR_CONSTRUCTOR == null ? 12 : 13];
        extractors[0] = new MatroskaExtractor(this.matroskaFlags);
        extractors[1] = new FragmentedMp4Extractor(this.fragmentedMp4Flags);
        extractors[2] = new Mp4Extractor(this.mp4Flags);
        extractors[3] = new Mp3Extractor(this.mp3Flags | (this.constantBitrateSeekingEnabled ? 1 : 0));
        extractors[4] = new AdtsExtractor(0L, this.adtsFlags | (this.constantBitrateSeekingEnabled ? 1 : 0));
        extractors[5] = new Ac3Extractor();
        extractors[6] = new TsExtractor(this.tsMode, this.tsFlags);
        extractors[7] = new FlvExtractor();
        extractors[8] = new OggExtractor();
        extractors[9] = new PsExtractor();
        extractors[10] = new WavExtractor();
        extractors[11] = new AmrExtractor(this.amrFlags | (this.constantBitrateSeekingEnabled ? 1 : 0));
        if (FLAC_EXTRACTOR_CONSTRUCTOR != null) {
            try {
                extractors[12] = FLAC_EXTRACTOR_CONSTRUCTOR.newInstance();
            } catch (Exception var3) {
                throw new IllegalStateException("Unexpected error creating FLAC extractor", var3);
            }
        }

        return extractors;
    }

    static {
        Constructor flacExtractorConstructor = null;

        try {
            flacExtractorConstructor = Class.forName("com.zj.playerLib.ext.flac.FlacExtractor").asSubclass(Extractor.class).getConstructor();
        } catch (ClassNotFoundException var2) {
        } catch (Exception var3) {
            throw new RuntimeException("Error instantiating FLAC extension", var3);
        }

        FLAC_EXTRACTOR_CONSTRUCTOR = flacExtractorConstructor;
    }
}
