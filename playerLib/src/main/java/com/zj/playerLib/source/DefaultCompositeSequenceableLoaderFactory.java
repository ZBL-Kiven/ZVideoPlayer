//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.source;

public final class DefaultCompositeSequenceableLoaderFactory implements CompositeSequenceableLoaderFactory {
    public DefaultCompositeSequenceableLoaderFactory() {
    }

    public SequenceableLoader createCompositeSequenceableLoader(SequenceableLoader... loaders) {
        return new CompositeSequenceableLoader(loaders);
    }
}
