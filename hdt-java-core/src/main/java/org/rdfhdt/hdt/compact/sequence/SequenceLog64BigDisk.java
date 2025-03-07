/**
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation;
 * version 3.0 of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Contacting the authors:
 *   Dennis Diefenbach:         dennis.diefenbach@univ-st-etienne.fr
 */
package org.rdfhdt.hdt.compact.sequence;

import org.rdfhdt.hdt.compact.integer.VByte;
import org.rdfhdt.hdt.hdt.HDTVocabulary;
import org.rdfhdt.hdt.listener.ProgressListener;
import org.rdfhdt.hdt.util.BitUtil;
import org.rdfhdt.hdt.util.crc.CRC32;
import org.rdfhdt.hdt.util.crc.CRC8;
import org.rdfhdt.hdt.util.crc.CRCOutputStream;
import org.rdfhdt.hdt.util.disk.LongArrayDisk;
import org.rdfhdt.hdt.util.io.IOUtil;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;

public class SequenceLog64BigDisk implements DynamicSequence, Closeable {
    private static final byte W = 64;
    private static final int INDEX = 1073741824;

    LongArrayDisk data;
    private int numbits;
    private long numentries=0;
    private long maxvalue;

    public SequenceLog64BigDisk(String location) {
        this(location, W);
    }

    public SequenceLog64BigDisk(String location, int numbits) {
        this(location, numbits, 0);
    }

    public SequenceLog64BigDisk(String location, int numbits, long capacity) {
        this.numentries = 0;
        this.numbits = numbits;
        this.maxvalue = BitUtil.maxVal(numbits);
        long size = numWordsFor(numbits, capacity);
        data = new LongArrayDisk(location, Math.max(size,1));
    }

    public SequenceLog64BigDisk(String location, int numbits, long capacity, boolean initialize) {
        this(location, numbits, capacity);
        if(initialize) {
            numentries = capacity;
        }
    }

    /** longs required to represent "total" integers of "bitsField" bits each */
    public static long numWordsFor(int bitsField, long total) {
        return ((bitsField*total+63)/64);
    }

    /** Number of bits required for last word */
    public static long lastWordNumBits(int bitsField, long total) {
        long totalBits = bitsField*total;
        if(totalBits==0) {
            return 0;
        }
        return (totalBits-1) % W +1;	// +1 To have output in the range 1-64, -1 to compensate.
    }

    /** Number of bits required for last word */
    public static long lastWordNumBytes(int bitsField, long total) {
        return ((lastWordNumBits(bitsField, total)-1)/8)+1;	// +1 To have output in the range 1-8, -1 to compensate.
    }

    /** Number of bytes required to represent n integers of e bits each */
    public static long numBytesFor(int bitsField, long total) {
        return (bitsField*total+7)/8;
    }

    /** Retrieve a given index from array data where every value uses bitsField bits
     * @param data Array
     * @param bitsField Length in bits of each field
     * @param index Position to be retrieved
     */
    private static long getField(LongArrayDisk data, int bitsField, long index) {
        if(bitsField==0) return 0;

        long bitPos = index*bitsField;
        long i= bitPos / W;
        long j= bitPos % W;
        long result;
        if (j+bitsField <= W) {
            result = (data.get(i) << (W-j-bitsField)) >>> (W-bitsField);
        } else {
            result = data.get(i) >>> j;
            result = result | (data.get(i+1) << ( (W<<1) -j-bitsField)) >>> (W-bitsField);
        }
        return result;
    }

    /** Store a given value in index into array data where every value uses bitsField bits
     * @param data Array
     * @param bitsField Length in bits of each field
     * @param index Position to store in
     * @param value Value to be stored
     */
    private static void setField(LongArrayDisk data, int bitsField, long index, long value) {
        if(bitsField==0) return;
        long bitPos = index*bitsField;
        long i= bitPos/W;
        long j= bitPos%W;
        long mask = ~(~0L << bitsField) << j;
        data.set(i, (data.getLong(i) & ~mask) | (value << j));

        if((j+bitsField>W)) {
            mask = ~0L << (bitsField+j-W);
            data.set(i+1 , (data.get(i+1) & mask) | value >>> (W-j));
        }
    }

    private void resizeArray(long size) {
        data.resize(size);
    }



    /* (non-Javadoc)
     * @see hdt.triples.array.Stream#add(java.util.Iterator)
     */
    @Override
    public void add(Iterator<Long> elements) { }

    public void addIntegers(ArrayList<Integer> elements) { }

    /* (non-Javadoc)
     * @see hdt.triples.array.Stream#get(long)
     */
    @Override
    public long get(long position) {
//		if(position<0 || position>=numentries) {
        //System.out.println("pos, numentries:"+position+","+numentries);
        //throw new IndexOutOfBoundsException();
//		}

        return getField(data, numbits, position);
    }

    @Override
    public void set(long position, long value) {
        //if(value<0 || value>maxvalue) {
        //throw new IllegalArgumentException("Value exceeds the maximum for this data structure");
        //}
        //System.out.println("numbits "+this.numbits);
        setField(data, numbits, position, value);
    }

    @Override
    public void append(long value) {

        //assert numentries<Integer.MAX_VALUE;

        //if(value<0 || value>maxvalue) {
        //throw new IllegalArgumentException("Value exceeds the maximum for this data structure");
        //}

        long neededSize = numWordsFor(numbits, numentries+1);
        if(data.length()<neededSize) {
            resizeArray(data.length()*2);
        }
        this.set(numentries, value);
        numentries++;
    }

    @Override
    public void aggressiveTrimToSize() {
        long max = 0;
        // Count and calculate number of bits needed per element.
        for(long i=0; i<numentries; i++) {
            long value = this.get(i);
            max = value>max ? value : max;
        }
        int newbits = BitUtil.log2(max);

        assert newbits <= numbits;
        //System.out.println("newbits"+newbits);
        if(newbits!=numbits) {
            for(long i=0;i<numentries;i++) {
                long value = getField(data, numbits, i);
                setField(data, newbits, i, value);
            }
            numbits = newbits;
            maxvalue = BitUtil.maxVal(numbits);

            long totalSize = numWordsFor(numbits, numentries);

            if (totalSize!=data.length()){
                resizeArray((int)totalSize);
            }
        }

    }

    @Override
    public void trimToSize() {
        resizeArray(numWordsFor(numbits, numentries));
    }

    public void resize(long numentries) {
        this.numentries = numentries;
        resizeArray(numWordsFor(numbits, numentries));
    }

    /* (non-Javadoc)
     * @see hdt.triples.array.Stream#getNumberOfElements()
     */
    @Override
    public long getNumberOfElements() {
        return numentries;
    }

    /* (non-Javadoc)
     * @see hdt.triples.array.Stream#save(java.io.OutputStream, hdt.ProgressListener)
     */
    @Override
    public void save(OutputStream output, ProgressListener listener) throws IOException {
        CRCOutputStream out = new CRCOutputStream(output, new CRC8());
        //write type of blocks
        out.write(SequenceFactory.TYPE_SEQLOG);
        //write the number of bits
        out.write(numbits);
        VByte.encode(out, numentries);
        //write CRC
        out.writeCRC();

        out.setCRC(new CRC32());

        long numwords = numWordsFor(numbits, numentries);
        for(long i=0;i<numwords-1;i++) {
            IOUtil.writeLong(out, data.getLong(i));
        }

        if(numwords>0) {
            // Write only used bits from last entry (byte aligned, little endian)
            long lastWordUsedBits = lastWordNumBits(numbits, numentries);
            BitUtil.writeLowerBitsByteAligned(data.get(numwords-1), lastWordUsedBits, out);
        }

        out.writeCRC();
    }

    @Override
    public void load(InputStream input, ProgressListener listener) { }

    /* (non-Javadoc)
     * @see hdt.triples.array.Stream#size()
     */
    @Override
    public long size() {
        return numBytesFor(numbits, numentries);
    }

    public long getRealSize() {
        return data.length()*8L;
    }

    public int getNumBits() {
        return numbits;
    }

    /* (non-Javadoc)
     * @see hdt.compact.array.Stream#getType()
     */
    @Override
    public String getType() {
        return HDTVocabulary.SEQ_TYPE_LOG;
    }

    @Override
    public void close() throws IOException {
        if (data != null) {
            data.close();
        }
        data=null;
    }
}