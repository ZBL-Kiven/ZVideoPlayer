//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.upstream.cache;

import android.util.SparseArray;
import android.util.SparseBooleanArray;

import com.zj.playerLib.upstream.cache.Cache.CacheException;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.AtomicFile;
import com.zj.playerLib.util.ReusableBufferedOutputStream;
import com.zj.playerLib.util.Util;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

class CachedContentIndex {
    public static final String FILE_NAME = "cached_content_index.exi";
    private static final int VERSION = 2;
    private static final int FLAG_ENCRYPTED_INDEX = 1;
    private final HashMap<String, CachedContent> keyToContent;
    private final SparseArray<String> idToKey;
    private final SparseBooleanArray removedIds;
    private final AtomicFile atomicFile;
    private final Cipher cipher;
    private final SecretKeySpec secretKeySpec;
    private final boolean encrypt;
    private boolean changed;
    private ReusableBufferedOutputStream bufferedOutputStream;

    public CachedContentIndex(File cacheDir) {
        this(cacheDir, (byte[]) null);
    }

    public CachedContentIndex(File cacheDir, byte[] secretKey) {
        this(cacheDir, secretKey, secretKey != null);
    }

    public CachedContentIndex(File cacheDir, byte[] secretKey, boolean encrypt) {
        this.encrypt = encrypt;
        if (secretKey != null) {
            Assertions.checkArgument(secretKey.length == 16);

            try {
                this.cipher = getCipher();
                this.secretKeySpec = new SecretKeySpec(secretKey, "AES");
            } catch (NoSuchPaddingException | NoSuchAlgorithmException var5) {
                throw new IllegalStateException(var5);
            }
        } else {
            Assertions.checkState(!encrypt);
            this.cipher = null;
            this.secretKeySpec = null;
        }

        this.keyToContent = new HashMap();
        this.idToKey = new SparseArray();
        this.removedIds = new SparseBooleanArray();
        this.atomicFile = new AtomicFile(new File(cacheDir, "cached_content_index.exi"));
    }

    public void load() {
        Assertions.checkState(!this.changed);
        if (!this.readFile()) {
            this.atomicFile.delete();
            this.keyToContent.clear();
            this.idToKey.clear();
        }

    }

    public void store() throws CacheException {
        if (this.changed) {
            this.writeFile();
            this.changed = false;
            int removedIdCount = this.removedIds.size();

            for (int i = 0; i < removedIdCount; ++i) {
                this.idToKey.remove(this.removedIds.keyAt(i));
            }

            this.removedIds.clear();
        }
    }

    public CachedContent getOrAdd(String key) {
        CachedContent cachedContent = (CachedContent) this.keyToContent.get(key);
        return cachedContent == null ? this.addNew(key) : cachedContent;
    }

    public CachedContent get(String key) {
        return (CachedContent) this.keyToContent.get(key);
    }

    public Collection<CachedContent> getAll() {
        return this.keyToContent.values();
    }

    public int assignIdForKey(String key) {
        return this.getOrAdd(key).id;
    }

    public String getKeyForId(int id) {
        return this.idToKey.get(id);
    }

    public void maybeRemove(String key) {
        CachedContent cachedContent = this.keyToContent.get(key);
        if (cachedContent != null && cachedContent.isEmpty() && !cachedContent.isLocked()) {
            this.keyToContent.remove(key);
            this.changed = true;
            this.idToKey.put(cachedContent.id, null);
            this.removedIds.put(cachedContent.id, true);
        }

    }

    public void removeEmpty() {
        String[] keys = new String[this.keyToContent.size()];
        this.keyToContent.keySet().toArray(keys);
        String[] var2 = keys;
        int var3 = keys.length;

        for (int var4 = 0; var4 < var3; ++var4) {
            String key = var2[var4];
            this.maybeRemove(key);
        }

    }

    public Set<String> getKeys() {
        return this.keyToContent.keySet();
    }

    public void applyContentMetadataMutations(String key, ContentMetadataMutations mutations) {
        CachedContent cachedContent = this.getOrAdd(key);
        if (cachedContent.applyMetadataMutations(mutations)) {
            this.changed = true;
        }

    }

    public ContentMetadata getContentMetadata(String key) {
        CachedContent cachedContent = this.get(key);
        return (ContentMetadata) (cachedContent != null ? cachedContent.getMetadata() : DefaultContentMetadata.EMPTY);
    }

    private boolean readFile() {
        DataInputStream input = null;

        boolean var4;
        try {
            InputStream inputStream = new BufferedInputStream(this.atomicFile.openRead());
            input = new DataInputStream(inputStream);
            int version = input.readInt();
            if (version >= 0 && version <= 2) {
                int flags = input.readInt();
                if ((flags & 1) != 0) {
                    if (this.cipher == null) {
                        return false;
                    }

                    byte[] initializationVector = new byte[16];
                    input.readFully(initializationVector);
                    IvParameterSpec ivParameterSpec = new IvParameterSpec(initializationVector);

                    try {
                        this.cipher.init(2, this.secretKeySpec, ivParameterSpec);
                    } catch (InvalidAlgorithmParameterException | InvalidKeyException var14) {
                        throw new IllegalStateException(var14);
                    }

                    input = new DataInputStream(new CipherInputStream(inputStream, this.cipher));
                } else if (this.encrypt) {
                    this.changed = true;
                }

                int count = input.readInt();
                int hashCode = 0;

                int fileHashCode;
                for (fileHashCode = 0; fileHashCode < count; ++fileHashCode) {
                    CachedContent cachedContent = CachedContent.readFromStream(version, input);
                    this.add(cachedContent);
                    hashCode += cachedContent.headerHashCode(version);
                }

                fileHashCode = input.readInt();
                boolean isEOF = input.read() == -1;
                return fileHashCode == hashCode && isEOF;
            }
        } catch (IOException var15) {
            return false;
        } finally {
            if (input != null) {
                Util.closeQuietly(input);
            }
        }
        return false;
    }

    private void writeFile() throws CacheException {
        DataOutputStream output = null;

        try {
            OutputStream outputStream = this.atomicFile.startWrite();
            if (this.bufferedOutputStream == null) {
                this.bufferedOutputStream = new ReusableBufferedOutputStream(outputStream);
            } else {
                this.bufferedOutputStream.reset(outputStream);
            }

            output = new DataOutputStream(this.bufferedOutputStream);
            output.writeInt(2);
            int flags = this.encrypt ? 1 : 0;
            output.writeInt(flags);
            if (this.encrypt) {
                byte[] initializationVector = new byte[16];
                (new Random()).nextBytes(initializationVector);
                output.write(initializationVector);
                IvParameterSpec ivParameterSpec = new IvParameterSpec(initializationVector);

                try {
                    this.cipher.init(1, this.secretKeySpec, ivParameterSpec);
                } catch (InvalidAlgorithmParameterException | InvalidKeyException var11) {
                    throw new IllegalStateException(var11);
                }

                output.flush();
                output = new DataOutputStream(new CipherOutputStream(this.bufferedOutputStream, this.cipher));
            }

            output.writeInt(this.keyToContent.size());
            int hashCode = 0;

            CachedContent cachedContent;
            for (Iterator<?> var15 = this.keyToContent.values().iterator(); var15.hasNext(); hashCode += cachedContent.headerHashCode(2)) {
                cachedContent = (CachedContent) var15.next();
                cachedContent.writeToStream(output);
            }

            output.writeInt(hashCode);
            this.atomicFile.endWrite(output);
            output = null;
        } catch (IOException var12) {
            throw new CacheException(var12);
        } finally {
            Util.closeQuietly(output);
        }
    }

    private CachedContent addNew(String key) {
        int id = getNewId(this.idToKey);
        CachedContent cachedContent = new CachedContent(id, key);
        this.add(cachedContent);
        this.changed = true;
        return cachedContent;
    }

    private void add(CachedContent cachedContent) {
        this.keyToContent.put(cachedContent.key, cachedContent);
        this.idToKey.put(cachedContent.id, cachedContent.key);
    }

    private static Cipher getCipher() throws NoSuchPaddingException, NoSuchAlgorithmException {
        if (Util.SDK_INT == 18) {
            try {
                return Cipher.getInstance("AES/CBC/PKCS5PADDING", "BC");
            } catch (Throwable ignored) {
            }
        }

        return Cipher.getInstance("AES/CBC/PKCS5PADDING");
    }

    public static int getNewId(SparseArray<String> idToKey) {
        int size = idToKey.size();
        int id = size == 0 ? 0 : idToKey.keyAt(size - 1) + 1;
        if (id < 0) {
            do {
                ++id;
            } while (id < size && id == idToKey.keyAt(id));
        }
        return id;
    }
}
