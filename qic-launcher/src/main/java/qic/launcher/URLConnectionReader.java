package qic.launcher;
import java.net.*;
import java.io.*;

public class URLConnectionReader {
    public static String getText(String url) {
        try {
        	URL website = new URL(url);
            URLConnection connection = website.openConnection();
            BufferedReader in = new BufferedReader(
                                    new InputStreamReader(
                                        connection.getInputStream()));

            StringBuilder response = new StringBuilder();
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
            	response.append(inputLine);
            	response.append(System.lineSeparator());
            } 

            in.close();

            return response.toString();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
    }

    public static void main(String[] args) throws Exception {
        String content = URLConnectionReader.getText(Main.CHANGELOG_URL);
        System.out.println(content);
    }
}