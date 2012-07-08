package naru.aweb.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class StreamUtil {
	/**
	 * InputStreamにあるデータを一気に読み込む
	 * 主にテスト用
	 * @param is
	 * @return
	 * @throws IOException
	 */
	public static byte[] readAll(InputStream is) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buf = new byte[1024];
		while (true) {
			int len = is.read(buf);
			if (len < 0) {
				break;
			}
			baos.write(buf, 0, len);
		}
		is.close();
		return baos.toByteArray();
	}

	/**
	 * remoteにinを書き込んで、出力を最後まで読み込んで返却する
	 * テスト用
	 * @param remote
	 * @param in
	 * @return
	 * @throws IOException
	 */
	public static byte[] socketIo(SocketAddress remote, byte[] in) throws IOException {
		Socket socket = new Socket();
		byte[] result;
		try {
			socket.connect(remote);
			OutputStream socketOut = socket.getOutputStream();
			socketOut.write(in);
			InputStream socketIn = socket.getInputStream();
			result = readAll(socketIn);
		} finally {
			socket.close();
		}
		return result;
	}
	
	public static void createFile(File file,InputStream is) throws IOException{
		FileOutputStream fos=new FileOutputStream(file);
		try {
			byte[] buffer=new byte[1024];
			while(true){
				int length=is.read(buffer);
				if(length<=0){
					break;
				}
				fos.write(buffer,0,length);
			}
		} finally {
			try {
				fos.close();
			} catch (IOException ignore) {
			}
		}
	}
	
	/* zipファイルをbaseに展開する　*/
	public static boolean unzip(File base,InputStream is){
		ZipInputStream zis = new ZipInputStream(is);
		try {
			String baseCan=base.getCanonicalPath();
			while (true) {
				ZipEntry ze = zis.getNextEntry();
				if (ze == null) {
					break;
				}
				String fileName = ze.getName();
				File file=new File(base,fileName);
				if(!file.getCanonicalPath().startsWith(baseCan)){
					return false;
				}
				if(file.exists()){
					return false;
				}
				if(ze.isDirectory()){
					file.mkdirs();
					continue;
				}
				File dir=file.getParentFile();
				dir.mkdirs();
				createFile(file,zis);
			}
		} catch (IOException e) {
			return false;
		}finally{
			try {
				zis.close();
			} catch (IOException ignore) {
			}
		}
		return true;
	}
}
