package naru.aweb.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;

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

}
