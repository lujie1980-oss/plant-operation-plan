import java.nio.file.*;
import java.util.zip.CRC32;
public class C {
  public static void main(String[] a) throws Exception {
    byte[] b = Files.readAllBytes(Path.of(a[0]));
    CRC32 c = new CRC32(); c.update(b);
    System.out.println((int)c.getValue());
  }
}
