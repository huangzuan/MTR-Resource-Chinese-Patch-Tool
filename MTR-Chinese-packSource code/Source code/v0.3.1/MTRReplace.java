// GUI组件（弹窗、列表、文件选择器）
import javax.swing.*;

// 文件读写
import java.io.*;

// 文件复制 / 移动
import java.nio.file.*;

// 数据结构（Map、List等）
import java.util.*;

// 读取jar文件
import java.util.jar.*;

// zip压缩操作（jar其实就是zip）
import java.util.zip.*;

// GUI相关
import java.awt.*;

// 定义程序类
public class MTRReplace {

    // MTR内部的一个特征class
    static String TARGET_CLASS =
            "org/mtr/libraries/okio/internal/-FileSystem$commonListRecursively$1.class";

    // MTR资源json
    static String TARGET_JSON =
            "assets/mtr/mtr_custom_resources.json";

    public static void main(String[] args) throws Exception {
        int count = 0; // 计数放在main方法里

        // 创建GUI
        JFrame frame = new JFrame("MTR Replace");
        JProgressBar bar = new JProgressBar();
        JLabel label = new JLabel(""); // 底部文字提示

        bar.setMinimum(0);
        bar.setMaximum(1);
        bar.setStringPainted(true);

        frame.setLayout(new BorderLayout());
        frame.add(bar, BorderLayout.CENTER);
        frame.add(label, BorderLayout.SOUTH);

        frame.setSize(300, 80);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // 加载程序图标 duke.png
        ImageIcon icon = new ImageIcon(MTRReplace.class.getResource("/duke.png"));

        // 系统信息
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

        // 官方启动器版本目录
        File versions = new File(mcRoot + "/versions");

        DefaultListModel<String> model = new DefaultListModel<>();
        Map<String, File> instanceMap = new HashMap<>();

        File officialMods = new File(mcRoot + "/mods");
        if (officialMods.exists()) {
            model.addElement("官方启动器 mods");
            instanceMap.put("官方启动器 mods", officialMods);
        }

        // 扫描官方versions
        if (versions.exists()) {
            File[] instances = versions.listFiles(File::isDirectory);
            if (instances != null) {
                for (File inst : instances) {
                    File mods = new File(inst, "mods");
                    if (!mods.exists()) continue;

                    File[] jars = mods.listFiles((d, n) -> n.endsWith(".jar"));
                    if (jars == null) continue;

                    for (File jar : jars) {
                        if (isMTRJar(jar)) {
                            label.setText("正在替换中，请勿关闭程序");
                            bar.setValue(1);
                            bar.setString(jar.getName());

                            backupJar(jar);
                            replaceJson(jar);
                            count++;
                        }
                    }

                    // 判断是否是MTR
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
        }

        // 其他启动器实例目录
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
            instanceRoots = new File[]{
                    new File(home + "/.local/share/PrismLauncher/instances"),
                    new File(home + "/.local/share/MultiMC/instances"),
                    new File(home + "/.local/share/ModrinthApp/profiles"),
                    new File(home + "/.local/share/com.modrinth.theseus/profiles"),
                    new File(home + "/.curseforge/minecraft/Instances")
            };
        }

        // 扫描启动器实例
        for (File root : instanceRoots) {
            if (!root.exists()) continue;

            File[] insts = root.listFiles(File::isDirectory);
            if (insts == null) continue;

            for (File inst : insts) {
                File mods1 = new File(inst, ".minecraft/mods");
                File mods2 = new File(inst, "mods");
                File mods = mods1.exists() ? mods1 : mods2.exists() ? mods2 : null;
                if (mods == null) continue;

                File[] jars = mods.listFiles((d, n) -> n.endsWith(".jar"));
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

        // 手动选择mods
        model.addElement("手动选择 mods 文件夹");

        // 如果没找到MTR
        if (model.isEmpty()) {
            JOptionPane.showMessageDialog(null, "没有找到包含 MTR 的实例");
            return;
        }

        // 列表GUI
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
            JOptionPane.showMessageDialog(null, "mods 文件夹不存在");
            return;
        }

        File[] jars = mods.listFiles((d, n) -> n.endsWith(".jar"));
        if (jars == null) return;

        count = 0;
        for (File jar : jars) {
            if (isMTRJar(jar)) {
                backupJar(jar);
                replaceJson(jar);
                count++;
            }
        }

        JOptionPane.showMessageDialog(null, "完成，修改了 " + count + " 个 MTR jar");
        frame.dispose();
    }

    static boolean isMTRJar(File jar) {
        if (!jar.getName().toLowerCase().contains("mtr")) return false;
        try (JarFile jf = new JarFile(jar)) {
            ZipEntry entry = jf.getEntry("fabric.mod.json");
            if (entry == null) return false;
            InputStream in = jf.getInputStream(entry);
            String json = new String(in.readAllBytes());
            if (!json.contains("\"id\": \"mtr\"")) return false;
            if (jf.getEntry("assets/mtr") == null) return false;
            return true;
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
            if (name.equals("assets/mtr/mtr_custom_resources.json")
                    || name.equals("assets/mtr/lang/en_us.json")
                    || name.equals("assets/mtr/lang/zh_cn.json")) {

                zout.putNextEntry(new ZipEntry(name));
                String fileName = name.substring(name.lastIndexOf("/") + 1);
                InputStream replaceStream = MTRReplace.class.getResourceAsStream("/" + fileName);
                if (replaceStream != null) replaceStream.transferTo(zout);
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