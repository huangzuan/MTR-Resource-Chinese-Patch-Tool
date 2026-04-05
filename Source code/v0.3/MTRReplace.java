import javax.swing.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import javax.swing.*;
import java.awt.*;

public class MTRReplace {

    static String TARGET_CLASS = "org/mtr/libraries/okio/internal/-FileSystem$commonListRecursively$1.class";
    static String TARGET_JSON = "assets/mtr/mtr_custom_resources.json";

    public static void main(String[] args) throws Exception {
        ImageIcon icon = new ImageIcon(
        MTRReplace.class.getResource("/duke.png")
        );

        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");

        String mcRoot;

        if (os.contains("win")) {
            mcRoot = System.getenv("APPDATA") + "\\.minecraft";
        } else if (os.contains("mac")) {
            mcRoot = home + "/Library/Application Support/minecraft";
        } else {
            mcRoot = home + "/.minecraft";
        }

        File versions = new File(mcRoot + "/versions");

        DefaultListModel<String> model = new DefaultListModel<>();
        Map<String, File> instanceMap = new HashMap<>();

        // 官方 mods
        File officialMods = new File(mcRoot + "/mods");
        if (officialMods.exists()) {
            model.addElement("官方启动器 mods");
            instanceMap.put("官方启动器 mods", officialMods);
        }

        // 扫描 versions
        if (versions.exists()) {

            File[] instances = versions.listFiles(File::isDirectory);

            if (instances != null)
            for (File inst : instances) {

                File mods = new File(inst, "mods");
                if (!mods.exists()) continue;

                File[] jars = mods.listFiles((d,n)->n.endsWith(".jar"));
                if (jars == null) continue;

                for (File jar : jars) {

                    if (isMTRJar(jar)) {

                        String name = "Minecraft - " + inst.getName();
                        model.addElement(name);
                        instanceMap.put(name, mods);
                        break;

                    }

                }

            }

        }

        // 启动器实例目录
File[] instanceRoots;

if (os.contains("win")) {

    String appdata = System.getenv("APPDATA");

    instanceRoots = new File[]{
            new File(appdata + "\\PrismLauncher\\instances"),
            new File(appdata + "\\MultiMC\\instances"),
            new File(appdata + "\\com.modrinth.theseus\\profiles"),
            new File(home + "\\curseforge\\minecraft\\Instances")
    };

} else if (os.contains("mac")) {

    instanceRoots = new File[]{
            new File(home + "/Library/Application Support/PrismLauncher/instances"),
            new File(home + "/Library/Application Support/MultiMC/instances"),
            new File(home + "/Library/Application Support/ModrinthApp/profiles"),
            new File(home + "/curseforge/minecraft/Instances")
    };

} else {

    // Linux
    instanceRoots = new File[]{
            new File(home + "/.local/share/PrismLauncher/instances"),
            new File(home + "/.local/share/MultiMC/instances"),
            new File(home + "/.local/share/ModrinthApp/profiles"),
            new File(home + "/.local/share/com.modrinth.theseus/profiles"),
            new File(home + "/.curseforge/minecraft/Instances")
    };

}

        for (File root : instanceRoots) {

            if (!root.exists()) continue;

            File[] insts = root.listFiles(File::isDirectory);
            if (insts == null) continue;

            for (File inst : insts) {

                File mods1 = new File(inst, ".minecraft/mods");
                File mods2 = new File(inst, "mods");

                File mods = mods1.exists() ? mods1 : mods2.exists() ? mods2 : null;

                if (mods == null) continue;

                File[] jars = mods.listFiles((d,n)->n.endsWith(".jar"));
                if (jars == null) continue;

                for (File jar : jars) {

                    if (isMTRJar(jar)) {

                        String name = root.getName() + " - " + inst.getName();
                        model.addElement(name);
                        instanceMap.put(name, mods);
                        break;

                    }

                }

            }

        }

        // 手动选择
        model.addElement("手动选择 mods 文件夹");

        if (model.isEmpty()) {
            JOptionPane.showMessageDialog(null,"没有找到包含 MTR 的实例");
            return;
        }

        JList<String> list = new JList<>(model);

        int result = JOptionPane.showConfirmDialog(
        null,
        new JScrollPane(list),
        "选择实例",
        JOptionPane.OK_CANCEL_OPTION,
        JOptionPane.PLAIN_MESSAGE,
        icon
);
        if (result != JOptionPane.OK_OPTION) return;

        String selected = list.getSelectedValue();

        File mods;

        if (selected.equals("手动选择 mods 文件夹")) {

            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

            int r = chooser.showOpenDialog(null);

            if (r != JFileChooser.APPROVE_OPTION) return;

            mods = chooser.getSelectedFile();

        } else {

            mods = instanceMap.get(selected);

        }

        if (mods == null || !mods.exists()) {
            JOptionPane.showMessageDialog(null,"mods 文件夹不存在");
            return;
        }

        File[] jars = mods.listFiles((d,n)->n.endsWith(".jar"));
        if (jars == null) return;

        int count = 0;

        for (File jar : jars) {

            if (isMTRJar(jar)) {

                backupJar(jar);
                replaceJson(jar);

                count++;

            }

        }

        JOptionPane.showMessageDialog(null,"完成，修改了 "+count+" 个 MTR jar");

    }

    static boolean isMTRJar(File jar) {

        try (JarFile jf = new JarFile(jar)) {

            ZipEntry classEntry = jf.getEntry(TARGET_CLASS);
            ZipEntry jsonEntry = jf.getEntry(TARGET_JSON);

            return classEntry != null && jsonEntry != null;

        } catch (Exception e) {
            return false;
        }
    }

    static void backupJar(File jar) throws Exception {

        File backup = new File(jar.getParent(), jar.getName() + ".jar.backup");

        if (backup.exists()) return;

        Files.copy(jar.toPath(), backup.toPath());

    }

    static void replaceJson(File jar) throws Exception {

        File temp = new File(jar.getParent(), "temp.jar");

        ZipInputStream zin = new ZipInputStream(new FileInputStream(jar));
        ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(temp));

        ZipEntry entry;

        while ((entry = zin.getNextEntry()) != null) {

            String name = entry.getName();

            if (name.equals("assets/mtr/mtr_custom_resources.json") ||
                name.equals("assets/mtr/lang/en_us.json") ||
                name.equals("assets/mtr/lang/zh_cn.json")) {

                zout.putNextEntry(new ZipEntry(name));

                String fileName = name.substring(name.lastIndexOf("/") + 1);

InputStream replaceStream =
        MTRReplace.class.getResourceAsStream("/" + fileName);

if (replaceStream != null) {
    replaceStream.transferTo(zout);
}


            } else {

                zout.putNextEntry(new ZipEntry(name));
                zin.transferTo(zout);

            }

        }

        zin.close();
        zout.close();

        Files.delete(jar.toPath());
        Files.move(temp.toPath(), jar.toPath());

    }
}