package util;

import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Key;
import java.util.Base64;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Created by spring on 6/13/16.
 */
public class Writer {

    private Writer(){
        properties = new Properties();
    }

    private static final Logger logger = Logger.getLogger("log");
    private static Writer instance;
    private static Properties properties;
    private static final Security security = Security.getInstance();

    public static Writer getInstance(){
        if (instance == null){
            synchronized (Writer.class){
                if (instance == null){
                    instance = new Writer();
                }
            }
        }
        return instance;
    }

    private Path checkPropertyFile(){
        try {
            Path propFile = Paths.get("config.properties");
            if (!Files.exists(propFile, LinkOption.NOFOLLOW_LINKS)) {
                Files.createFile(propFile);
            }
            return propFile;
        }
        catch (IOException ex){
            ex.printStackTrace();
            logger.throwing("Analyser", "checkPropFile", ex);
        }
        return null;
    }

    public void writeProperty(String key, String value){
        try {
            Path propFile = checkPropertyFile();
            OutputStream output = new FileOutputStream(propFile.toFile().getPath());
            Key pubKey = Security.generateKey();
            String encValue = security.encrypt(pubKey, value);
            properties.setProperty(key, String.format("%s|%s", Base64.getEncoder().encodeToString(pubKey.getEncoded()), encValue));
            properties.store(output, null);
            output.close();
        }
        catch (Exception ex){
            ex.printStackTrace();
            logger.throwing("Analyser", "writeProperty", ex);
        }
    }

    public String getProperty(String key){
        try {
            Path propFile = checkPropertyFile();
            InputStream input = new FileInputStream(propFile.toFile().getPath());
            properties.load(input);
            String result = properties.getProperty(key);
            if (result == null) {
                input.close();
                return result;
            }
            String pubKey = result.substring(0, result.indexOf("|"));
            byte[] pubkeyBytes = Base64.getDecoder().decode(pubKey);
            result = result.substring(result.indexOf("|") + 1, result.length());
            input.close();
            return security.decrypt(new SecretKeySpec(pubkeyBytes, 0, pubkeyBytes.length, "DES"), result);
        }
        catch (Exception ex){
            ex.printStackTrace();
            logger.throwing("Analyser", "getProperty", ex);
        }
        return null;
    }

}
