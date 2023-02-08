package com.example.demo;

import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import javafx.util.Pair;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileManager {

    private static String[] extensions = new String[] {
            ".CR2", ".CR3", ".NEF",
            ".Erw", ".3fr", ".Raf",
            ".Arw", ".Srw", ".dng",
            ".tiff", ".png"
    };

    public static Optional<Pair<List<Path>, String>> lastPhotoMoved = Optional.empty();

    public static Comparator<File> compareFiles() {
        return new Comparator<File>() {

            @Override
            public int compare(File o1, File o2) {
                try {
                    BasicFileAttributes attr1 = Files.readAttributes(Path.of(o1.getAbsolutePath()), BasicFileAttributes.class);
                    FileTime t1 = attr1.creationTime();
                    BasicFileAttributes attr2 = Files.readAttributes(Path.of(o2.getAbsolutePath()), BasicFileAttributes.class);
                    FileTime t2 = attr2.creationTime();
                    return t1.compareTo(t2);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    public static List<String> getListOfImages(String path, Optional<String> dest, boolean onlyJpeg, boolean checkFile, ObservableList<String> list) {

        File dir = new File(path);
        File dirDest;
        List<String> subFolders;
        if(dest.isPresent()) {
            if(onlyJpeg) {
                System.out.println(dest.get());
                dirDest = new File(dest.get());
            } else {
                String newPath = dest.get() + File.separator + "JPEG" + File.separator;
                dirDest = new File(newPath);
            }
            if(dirDest.exists()) {
                subFolders = Arrays.stream(dirDest.listFiles(File::isDirectory)).map(File::getAbsolutePath).collect(Collectors.toList());
            } else {
                subFolders = new ArrayList<>();
            }
        } else {
            subFolders = new ArrayList<>();
        }

        File[] files = dir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                boolean isJpg = (name.toLowerCase().endsWith(".jpg")
                        || name.toLowerCase().endsWith(".jpeg"));

                boolean exist = subFolders.stream().map(
                        i -> checkFile(i, name) && checkFile
                ).reduce(false, (condFinal, cond) -> condFinal || cond);

                boolean finalCondition = isJpg && !exist;
                if(finalCondition) list.add(name);
                return finalCondition;
            }
        });

        return Arrays.stream(files).sorted(FileManager.compareFiles()).map(i -> i.getName()).collect(Collectors.toList());
    }

    public static boolean checkFile(String path, String fileName) {
        File dir = new File(path);
        String[] files = dir.list();
        for(String file : files) {
            if(file.toLowerCase().equals(fileName.toLowerCase())) {
                return true;
            }
        }
        //metodo alternativo: Files.exists(Paths.get(path + File.separator + fileName));
        return false;
    }

    public static void moveImage(String source, String to, String nameFolder, String nameFile, boolean onlyJpg) {
        try {
            Path url = copyFile(source, to, nameFolder, nameFile, false, onlyJpg);
            ArrayList<Path> paths = new ArrayList<>();
            paths.add(url);
            String[] parts = nameFile.split("\\.");

            if(!onlyJpg) {
                Arrays.stream(extensions).map(i -> parts[0].concat(i)).filter(i -> checkFile(source, i)).forEach(i -> {
                    try {
                        Path tmpUrl = copyFile(source, to, nameFolder, i, true, onlyJpg);
                        paths.add(tmpUrl);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }

            lastPhotoMoved = Optional.of(new Pair<>(paths, nameFile));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Path copyFile(String source, String to, String nameFolder, String nameFile, boolean raw, boolean onlyJpeg) throws IOException {
        String type = "";
        if(!onlyJpeg) {
            type = raw ? "RAW" : "JPEG";
            type = type.concat(File.separator);
        }

        Path copied = Paths.get(to + File.separator + type + nameFolder + File.separator + nameFile);
        Path originalPath = Paths.get(source + File.separator + nameFile);
        File f = new File(to + File.separator + type + nameFolder);
        f.mkdirs();
        Files.copy(originalPath, copied);
        return copied;
    }

    public static void deleteFile() {
        if(lastPhotoMoved.isPresent()) {
            lastPhotoMoved.get().getKey().forEach(i -> {
                File toDelete = new File(i.toString());
                if(toDelete.delete()) {
                    System.out.println("ELIMINATO");
                    lastPhotoMoved = Optional.empty();
                } else {
                    System.out.println("ERRORE");
                }
            });
        }
    }

    public static boolean isThereAtLeastARaw(String path) {
        File file = new File(path);
        String regex = Arrays.stream(extensions).collect(Collectors.joining("|"));
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        return Arrays.stream(file.listFiles()).filter(i -> pattern.matcher(i.getName()).find()).count() > 0;
    }

}
