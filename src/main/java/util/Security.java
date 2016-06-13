package util;

import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import java.security.Key;

public class Security {

    private Security(){
        try {
            cipher = Cipher.getInstance("DES");
        }
        catch (Exception ex) { }
    }

    private static Security instance;
    private Cipher cipher;

    public static Security getInstance(){
        if (instance == null){
            instance = new Security();
        }
        return instance;
    }

    public String encrypt(Key key, String str){
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] utf8 = str.getBytes("UTF8");
            byte[] enc = cipher.doFinal(utf8);
            return new BASE64Encoder().encode(enc);
        }
        catch (Exception ex){ }
        return null;
    }

    public String decrypt(Key key, String str){
        try{
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] dec = new BASE64Decoder().decodeBuffer(str);
            byte[] utf8 = cipher.doFinal(dec);
            return new String(utf8, "UTF8");
        }
        catch (Exception ex){ }
        return null;
    }

    public static Key generateKey(){
        try {
            return KeyGenerator.getInstance("DES").generateKey();
        }
        catch (Exception ex) { }
        return null;
    }
}
