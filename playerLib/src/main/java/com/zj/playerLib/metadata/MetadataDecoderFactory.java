//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.metadata;

import com.zj.playerLib.Format;
import com.zj.playerLib.metadata.emsg.EventMessageDecoder;
import com.zj.playerLib.metadata.id3.Id3Decoder;
import com.zj.playerLib.metadata.scte35.SpliceInfoDecoder;

public interface MetadataDecoderFactory {
    MetadataDecoderFactory DEFAULT = new MetadataDecoderFactory() {
        public boolean supportsFormat(Format format) {
            String mimeType = format.sampleMimeType;
            return "application/id3".equals(mimeType) || "application/x-emsg".equals(mimeType) || "application/x-scte35".equals(mimeType);
        }

        public MetadataDecoder createDecoder(Format format) {
            String var2 = format.sampleMimeType;
            byte var3 = -1;
            switch(var2.hashCode()) {
            case -1248341703:
                if (var2.equals("application/id3")) {
                    var3 = 0;
                }
                break;
            case 1154383568:
                if (var2.equals("application/x-emsg")) {
                    var3 = 1;
                }
                break;
            case 1652648887:
                if (var2.equals("application/x-scte35")) {
                    var3 = 2;
                }
            }

            switch(var3) {
            case 0:
                return new Id3Decoder();
            case 1:
                return new EventMessageDecoder();
            case 2:
                return new SpliceInfoDecoder();
            default:
                throw new IllegalArgumentException("Attempted to create decoder for unsupported format");
            }
        }
    };

    boolean supportsFormat(Format var1);

    MetadataDecoder createDecoder(Format var1);
}
