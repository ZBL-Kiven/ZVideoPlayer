package com.zj.playerLib.text;

import com.zj.playerLib.Format;
import com.zj.playerLib.text.cea.Cea608Decoder;
import com.zj.playerLib.text.cea.Cea708Decoder;
import com.zj.playerLib.text.dvb.DvbDecoder;
import com.zj.playerLib.text.pgs.PgsDecoder;
import com.zj.playerLib.text.ssa.SsaDecoder;
import com.zj.playerLib.text.subrip.SubripDecoder;
import com.zj.playerLib.text.ttml.TtmlDecoder;
import com.zj.playerLib.text.tx3g.Tx3gDecoder;
import com.zj.playerLib.text.webvtt.Mp4WebvttDecoder;
import com.zj.playerLib.text.webvtt.WebvttDecoder;

public interface SubtitleDecoderFactory {
    SubtitleDecoderFactory DEFAULT = new SubtitleDecoderFactory() {
        public boolean supportsFormat(Format format) {
            String mimeType = format.sampleMimeType;
            return "text/vtt".equals(mimeType) || "text/x-ssa".equals(mimeType) || "application/ttml+xml".equals(mimeType) || "application/x-mp4-vtt".equals(mimeType) || "application/x-subrip".equals(mimeType) || "application/x-quicktime-tx3g".equals(mimeType) || "application/cea-608".equals(mimeType) || "application/x-mp4-cea-608".equals(mimeType) || "application/cea-708".equals(mimeType) || "application/dvbsubs".equals(mimeType) || "application/pgs".equals(mimeType);
        }

        public SubtitleDecoder createDecoder(Format format) {
            String var2 = format.sampleMimeType;
            byte var3 = -1;
            switch(var2.hashCode()) {
            case -1351681404:
                if (var2.equals("application/dvbsubs")) {
                    var3 = 9;
                }
                break;
            case -1248334819:
                if (var2.equals("application/pgs")) {
                    var3 = 10;
                }
                break;
            case -1026075066:
                if (var2.equals("application/x-mp4-vtt")) {
                    var3 = 2;
                }
                break;
            case -1004728940:
                if (var2.equals("text/vtt")) {
                    var3 = 0;
                }
                break;
            case 691401887:
                if (var2.equals("application/x-quicktime-tx3g")) {
                    var3 = 5;
                }
                break;
            case 822864842:
                if (var2.equals("text/x-ssa")) {
                    var3 = 1;
                }
                break;
            case 930165504:
                if (var2.equals("application/x-mp4-cea-608")) {
                    var3 = 7;
                }
                break;
            case 1566015601:
                if (var2.equals("application/cea-608")) {
                    var3 = 6;
                }
                break;
            case 1566016562:
                if (var2.equals("application/cea-708")) {
                    var3 = 8;
                }
                break;
            case 1668750253:
                if (var2.equals("application/x-subrip")) {
                    var3 = 4;
                }
                break;
            case 1693976202:
                if (var2.equals("application/ttml+xml")) {
                    var3 = 3;
                }
            }

            switch(var3) {
            case 0:
                return new WebvttDecoder();
            case 1:
                return new SsaDecoder(format.initializationData);
            case 2:
                return new Mp4WebvttDecoder();
            case 3:
                return new TtmlDecoder();
            case 4:
                return new SubripDecoder();
            case 5:
                return new Tx3gDecoder(format.initializationData);
            case 6:
            case 7:
                return new Cea608Decoder(format.sampleMimeType, format.accessibilityChannel);
            case 8:
                return new Cea708Decoder(format.accessibilityChannel, format.initializationData);
            case 9:
                return new DvbDecoder(format.initializationData);
            case 10:
                return new PgsDecoder();
            default:
                throw new IllegalArgumentException("Attempted to create decoder for unsupported format");
            }
        }
    };

    boolean supportsFormat(Format var1);

    SubtitleDecoder createDecoder(Format var1);
}
