package qic.launcher;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

/**
 * @author Oleg Ryaboy, based on work by Miguel Enriquez 
 */
public class WindowsReqistry {

    /**
     * 
     * @param location path in the registry
     * @param key registry key
     * @return registry value or null if not found
     */
    public static final String readRegistry(String location, String key){
        try {
            // Run reg query, then read output with StreamReader (internal class)
            String command = "reg query " + 
                    '"'+ location + "\" /v " + key;
//            System.out.println(command);
			Process process = Runtime.getRuntime().exec(command);

            StreamReader reader = new StreamReader(process.getInputStream());
            reader.start();
            process.waitFor();
            reader.join();
            String output = reader.getResult();
//            System.out.println(output);
            // Output has the following format:
			//HKEY_LOCAL_MACHINE\SOFTWARE\AutoHotKey
			//    InstallDir    REG_SZ    C:\Program Files\AutoHotkey
//            if( ! output.contains("\t")){
//                    return null;
//            }
//            // Parse out the value
//            String[] parsed = output.split("\t");
//            return parsed[parsed.length-1];
            List<String> asList = Arrays.asList(output.split(System.lineSeparator()));
            Optional<String> findFirst = asList.stream()
            		.map(s -> s.trim())
            		.filter(s -> s.startsWith("InstallDir")).findFirst();
            String value = findFirst.map(s -> StringUtils.substringAfter(s, "REG_SZ").trim()).orElseGet(null);
            return value;
        }
        catch (Exception e) {
            return null;
        }

    }

    static class StreamReader extends Thread {
        private InputStream is;
        private StringWriter sw= new StringWriter();

        public StreamReader(InputStream is) {
            this.is = is;
        }

        public void run() {
            try {
                int c;
                while ((c = is.read()) != -1)
                    sw.write(c);
            }
            catch (IOException e) { 
        }
        }

        public String getResult() {
            return sw.toString();
        }
    }
    public static void main(String[] args) {
        // Sample usage
        String value = WindowsReqistry.readRegistry("HKEY_LOCAL_MACHINE\\SOFTWARE\\AutoHotKey", "InstallDir");
        System.out.println(value);
    }
}