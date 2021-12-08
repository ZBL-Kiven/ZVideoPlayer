package com.zj.playerLib.source;

import java.util.Arrays;
import java.util.Random;

public interface ShuffleOrder {
    int getLength();

    int getNextIndex(int var1);

    int getPreviousIndex(int var1);

    int getLastIndex();

    int getFirstIndex();

    ShuffleOrder cloneAndInsert(int var1, int var2);

    ShuffleOrder cloneAndRemove(int var1, int var2);

    ShuffleOrder cloneAndClear();

    final class UnshuffledShuffleOrder implements ShuffleOrder {
        private final int length;

        public UnshuffledShuffleOrder(int length) {
            this.length = length;
        }

        public int getLength() {
            return this.length;
        }

        public int getNextIndex(int index) {
            ++index;
            return index < this.length ? index : -1;
        }

        public int getPreviousIndex(int index) {
            --index;
            return index >= 0 ? index : -1;
        }

        public int getLastIndex() {
            return this.length > 0 ? this.length - 1 : -1;
        }

        public int getFirstIndex() {
            return this.length > 0 ? 0 : -1;
        }

        public ShuffleOrder cloneAndInsert(int insertionIndex, int insertionCount) {
            return new UnshuffledShuffleOrder(this.length + insertionCount);
        }

        public ShuffleOrder cloneAndRemove(int indexFrom, int indexToExclusive) {
            return new UnshuffledShuffleOrder(this.length - indexToExclusive + indexFrom);
        }

        public ShuffleOrder cloneAndClear() {
            return new UnshuffledShuffleOrder(0);
        }
    }

    class DefaultShuffleOrder implements ShuffleOrder {
        private final Random random;
        private final int[] shuffled;
        private final int[] indexInShuffled;

        public DefaultShuffleOrder(int length) {
            this(length, new Random());
        }

        public DefaultShuffleOrder(int length, long randomSeed) {
            this(length, new Random(randomSeed));
        }

        public DefaultShuffleOrder(int[] shuffledIndices, long randomSeed) {
            this(Arrays.copyOf(shuffledIndices, shuffledIndices.length), new Random(randomSeed));
        }

        private DefaultShuffleOrder(int length, Random random) {
            this(createShuffledList(length, random), random);
        }

        private DefaultShuffleOrder(int[] shuffled, Random random) {
            this.shuffled = shuffled;
            this.random = random;
            this.indexInShuffled = new int[shuffled.length];

            for(int i = 0; i < shuffled.length; this.indexInShuffled[shuffled[i]] = i++) {
            }

        }

        public int getLength() {
            return this.shuffled.length;
        }

        public int getNextIndex(int index) {
            int shuffledIndex = this.indexInShuffled[index];
            ++shuffledIndex;
            return shuffledIndex < this.shuffled.length ? this.shuffled[shuffledIndex] : -1;
        }

        public int getPreviousIndex(int index) {
            int shuffledIndex = this.indexInShuffled[index];
            --shuffledIndex;
            return shuffledIndex >= 0 ? this.shuffled[shuffledIndex] : -1;
        }

        public int getLastIndex() {
            return this.shuffled.length > 0 ? this.shuffled[this.shuffled.length - 1] : -1;
        }

        public int getFirstIndex() {
            return this.shuffled.length > 0 ? this.shuffled[0] : -1;
        }

        public ShuffleOrder cloneAndInsert(int insertionIndex, int insertionCount) {
            int[] insertionPoints = new int[insertionCount];
            int[] insertionValues = new int[insertionCount];

            int indexInOldShuffled;
            for(int i = 0; i < insertionCount; ++i) {
                insertionPoints[i] = this.random.nextInt(this.shuffled.length + 1);
                indexInOldShuffled = this.random.nextInt(i + 1);
                insertionValues[i] = insertionValues[indexInOldShuffled];
                insertionValues[indexInOldShuffled] = i + insertionIndex;
            }

            Arrays.sort(insertionPoints);
            int[] newShuffled = new int[this.shuffled.length + insertionCount];
            indexInOldShuffled = 0;
            int indexInInsertionList = 0;

            for(int i = 0; i < this.shuffled.length + insertionCount; ++i) {
                if (indexInInsertionList < insertionCount && indexInOldShuffled == insertionPoints[indexInInsertionList]) {
                    newShuffled[i] = insertionValues[indexInInsertionList++];
                } else {
                    newShuffled[i] = this.shuffled[indexInOldShuffled++];
                    if (newShuffled[i] >= insertionIndex) {
                        newShuffled[i] += insertionCount;
                    }
                }
            }

            return new DefaultShuffleOrder(newShuffled, new Random(this.random.nextLong()));
        }

        public ShuffleOrder cloneAndRemove(int indexFrom, int indexToExclusive) {
            int numberOfElementsToRemove = indexToExclusive - indexFrom;
            int[] newShuffled = new int[this.shuffled.length - numberOfElementsToRemove];
            int foundElementsCount = 0;

            for(int i = 0; i < this.shuffled.length; ++i) {
                if (this.shuffled[i] >= indexFrom && this.shuffled[i] < indexToExclusive) {
                    ++foundElementsCount;
                } else {
                    newShuffled[i - foundElementsCount] = this.shuffled[i] >= indexFrom ? this.shuffled[i] - numberOfElementsToRemove : this.shuffled[i];
                }
            }

            return new DefaultShuffleOrder(newShuffled, new Random(this.random.nextLong()));
        }

        public ShuffleOrder cloneAndClear() {
            return new DefaultShuffleOrder(0, new Random(this.random.nextLong()));
        }

        private static int[] createShuffledList(int length, Random random) {
            int[] shuffled = new int[length];

            int swapIndex;
            for(int i = 0; i < length; shuffled[swapIndex] = i++) {
                swapIndex = random.nextInt(i + 1);
                shuffled[i] = shuffled[swapIndex];
            }

            return shuffled;
        }
    }
}
