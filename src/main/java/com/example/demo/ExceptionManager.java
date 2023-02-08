package com.example.demo;

import javax.swing.filechooser.FileSystemView;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ExceptionManager {

    private static final ExceptionManager inst= new ExceptionManager();
    private String path = FileSystemView.getFileSystemView().getDefaultDirectory().getAbsolutePath();

    private ExceptionManager() {
        super();
    }

    public synchronized void writeToFile(String str) throws IOException {
        //get the time and date
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        //check che un file SmistaFoto_config.txt exists
        String pathFile = path + File.separator + "SmistaFoto_config.txt";
        boolean cond = Files.exists(Paths.get(pathFile));
        //create it or open it
        if(!cond) {
            Files.createFile(Paths.get(pathFile));
        }
        //append the string && close the file
        try (FileWriter f = new FileWriter(pathFile, true); BufferedWriter b = new BufferedWriter(f); PrintWriter p = new PrintWriter(b)) {
            p.println("--------------------------");
            p.println(dtf.format(now));
            p.println(str);
            p.println("--------------------------");
        } catch (IOException i) {
            i.printStackTrace();
        }
    }

    public static ExceptionManager getInstance() {
        return inst;
    }

}
