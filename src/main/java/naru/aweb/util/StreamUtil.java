package naru.aweb.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.log4j.Logger;

public class StreamUtil {
	private static Logger logger=Logger.getLogger(StreamUtil.class);
	
	public static int  indexOfByte( byte bytes[], int off, int end, char qq ){
        // Works only for UTF 
        while( off < end ) {
            byte b=bytes[off];
            if( b==qq )
                return off;
            off++;
        }
        return -1;
    }
	public static int  lastIndexOfByte( byte bytes[], int off, int end, char qq ){
        // Works only for UTF 
		end--;
        while( off < end ) {
            byte b=bytes[end];
            if( b==qq )
                return end;
            end--;
        }
        return -1;
    }
	
	public static byte[] readFile(File file) throws IOException {
		FileInputStream fis=new FileInputStream(file);
		return readAll(fis);
	}
	
	
	/**
	 * InputStream�ɂ���f�[�^����C�ɓǂݍ���
	 * ��Ƀe�X�g�p
	 * @param is
	 * @return
	 * @throws IOException
	 */
	public static byte[] readAll(InputStream is) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buf = new byte[1024];
		try {
			while (true) {
				int len = is.read(buf);
				if (len < 0) {
					break;
				}
				baos.write(buf, 0, len);
			}
		} finally {
			is.close();
		}
		return baos.toByteArray();
	}

	/**
	 * remote��in����������ŁA�o�͂��Ō�܂œǂݍ���ŕԋp����
	 * �e�X�g�p
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
	
	/**
	 *  zip�t�@�C����base�ɓW�J����
	 * @return �W�J�����t�@�C��,�f�B���N�g����
	 */
	public static int unzip(File base,InputStream is){
		ZipInputStream zis = new ZipInputStream(is);
		int count=0;
		try {
			int x=zis.available();
			String baseCan=base.getCanonicalPath();
			while (true) {
				ZipEntry ze = zis.getNextEntry();
				if (ze == null) {
					break;
				}
				String fileName = ze.getName();
				File file=new File(base,fileName);
				if(!file.getCanonicalPath().startsWith(baseCan)){
					return count;
				}
				if(file.exists()){
					return count;
				}
				count++;
				if(ze.isDirectory()){
					file.mkdirs();
					continue;
				}
				File dir=file.getParentFile();
				dir.mkdirs();
				createFile(file,zis);
			}
		} catch (IOException e) {
			logger.warn("unzip error",e);
			return count;
		}finally{
			try {
				zis.close();
			} catch (IOException ignore) {
			}
		}
		return count;
	}
}
