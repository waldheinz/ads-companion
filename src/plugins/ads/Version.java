/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.ads;

import freenet.crypt.SHA256;
import freenet.support.Base64;
import freenet.support.Fields;
import java.security.MessageDigest;
import java.util.Arrays;
import net.i2p.util.NativeBigInteger;

/**
 * @author saces
 *
 */
public class Version {

    /**
     * SVN revision number. Only set if the plugin is compiled properly e.g. by
     * emu.
     */
    public static final String gitRevision = "@custom@";

    /**
     * Version number of the plugin for getRealVersion(). Increment this on
     * making a major change, a significant bugfix etc. These numbers are used
     * in auto-update etc, at a minimum any build inserted into auto-update
     * should have a unique version.
     */
    public static final long version = 5020;

    public static final String longVersionString = "0.1" + gitRevision;

    /**
     * just prints the version number to standard out. intended to be used by
     * build scripts those depends on keyutils
     *
     * @param args
     */
    public static void main(String[] args) {
        System.out.println(version);
        toNormalizedDouble();
    }

    private static void reverse(byte[] validData) {
        for (int i = 0; i < validData.length / 2; i++) {
            byte temp = validData[i];
            validData[i] = validData[validData.length - i - 1];
            validData[validData.length - i - 1] = temp;
        }
        
    }

    public static synchronized void toNormalizedDouble() {
        MessageDigest md = SHA256.getMessageDigest();
        byte[] routingKey = new byte[32];
        
        for (int i=0; i < routingKey.length; i++) {
            routingKey[i] = 1;
        }
        
        if (routingKey == null) {
            throw new NullPointerException();
        }
        md.update(routingKey);
        int TYPE = 3;
        md.update((byte) (TYPE >> 8));
        md.update((byte) TYPE);
        byte[] digest = md.digest();
        System.out.println(Base64.encode(digest));
        SHA256.returnMessageDigest(md);
        md = null;
        long asLong = Math.abs(Fields.bytesToLong(digest));
        System.out.println("sein long = " + Fields.bytesToLong(digest));
//        reverse(digest);
        final NativeBigInteger asInt = new NativeBigInteger(1, digest);
        
        System.out.println("mein long = " + asInt.shiftRight(192));
        
        byte[] forReverse = asInt.shiftRight(192).toByteArray();
        reverse(forReverse);
        
        System.out.println("mein long'= " + new NativeBigInteger(1, forReverse));
        
        System.out.println(
                asInt.doubleValue() / new NativeBigInteger("256").pow(32).doubleValue());

        

        // Math.abs can actually return negative...
        if (asLong == Long.MIN_VALUE) {
            asLong = Long.MAX_VALUE;
        }

        System.out.println("ende = " + asLong);
        
        System.out.println(((double) asLong) / ((double) Long.MAX_VALUE));
    }

}
