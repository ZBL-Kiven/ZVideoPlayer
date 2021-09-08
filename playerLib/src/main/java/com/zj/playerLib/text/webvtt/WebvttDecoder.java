//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.text.webvtt;

import android.text.TextUtils;
import com.zj.playerLib.ParserException;
import com.zj.playerLib.text.SimpleSubtitleDecoder;
import com.zj.playerLib.text.SubtitleDecoderException;
import com.zj.playerLib.text.webvtt.WebvttCue.Builder;
import com.zj.playerLib.util.ParsableByteArray;
import java.util.ArrayList;
import java.util.List;

public final class WebvttDecoder extends SimpleSubtitleDecoder {
    private static final int EVENT_NONE = -1;
    private static final int EVENT_END_OF_FILE = 0;
    private static final int EVENT_COMMENT = 1;
    private static final int EVENT_STYLE_BLOCK = 2;
    private static final int EVENT_CUE = 3;
    private static final String COMMENT_START = "NOTE";
    private static final String STYLE_START = "STYLE";
    private final WebvttCueParser cueParser = new WebvttCueParser();
    private final ParsableByteArray parsableWebvttData = new ParsableByteArray();
    private final Builder webvttCueBuilder = new Builder();
    private final CssParser cssParser = new CssParser();
    private final List<WebvttCssStyle> definedStyles = new ArrayList();

    public WebvttDecoder() {
        super("WebvttDecoder");
    }

    protected WebvttSubtitle decode(byte[] bytes, int length, boolean reset) throws SubtitleDecoderException {
        this.parsableWebvttData.reset(bytes, length);
        this.webvttCueBuilder.reset();
        this.definedStyles.clear();

        try {
            WebvttParserUtil.validateWebvttHeaderLine(this.parsableWebvttData);
        } catch (ParserException var7) {
            throw new SubtitleDecoderException(var7);
        }

        while(!TextUtils.isEmpty(this.parsableWebvttData.readLine())) {
        }

        ArrayList subtitles = new ArrayList();

        int event;
        while((event = getNextEvent(this.parsableWebvttData)) != 0) {
            if (event == 1) {
                skipComment(this.parsableWebvttData);
            } else if (event == 2) {
                if (!subtitles.isEmpty()) {
                    throw new SubtitleDecoderException("A style block was found after the first cue.");
                }

                this.parsableWebvttData.readLine();
                WebvttCssStyle styleBlock = this.cssParser.parseBlock(this.parsableWebvttData);
                if (styleBlock != null) {
                    this.definedStyles.add(styleBlock);
                }
            } else if (event == 3 && this.cueParser.parseCue(this.parsableWebvttData, this.webvttCueBuilder, this.definedStyles)) {
                subtitles.add(this.webvttCueBuilder.build());
                this.webvttCueBuilder.reset();
            }
        }

        return new WebvttSubtitle(subtitles);
    }

    private static int getNextEvent(ParsableByteArray parsableWebvttData) {
        int foundEvent = -1;
        int currentInputPosition = 0;

        while(foundEvent == -1) {
            currentInputPosition = parsableWebvttData.getPosition();
            String line = parsableWebvttData.readLine();
            if (line == null) {
                foundEvent = 0;
            } else if ("STYLE".equals(line)) {
                foundEvent = 2;
            } else if (line.startsWith("NOTE")) {
                foundEvent = 1;
            } else {
                foundEvent = 3;
            }
        }

        parsableWebvttData.setPosition(currentInputPosition);
        return foundEvent;
    }

    private static void skipComment(ParsableByteArray parsableWebvttData) {
        while(!TextUtils.isEmpty(parsableWebvttData.readLine())) {
        }

    }
}
