package naru.aweb.filter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.zip.GZIPInputStream;

import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;

public class Targz {
	private static void read(InputStream listIs) throws IOException{
		BufferedReader reader=null;
		try {
			reader=new BufferedReader(new InputStreamReader(listIs,"iso8859_1"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		while(true){
			String filter=reader.readLine();
			if(filter==null){
				break;
			}
			System.out.println(filter);
		}
	}

	
	private static void targz(String targzName) throws IOException{
		FileInputStream fis = new FileInputStream(targzName);
		TarInputStream tin = new TarInputStream(new GZIPInputStream(fis)); 
		TarEntry tarEnt = tin.getNextEntry();
		while (tarEnt != null) {
		 //名前を取得
		 String name = tarEnt.getName();
		 //サイズを取得
		 int size = (int)tarEnt.getSize();
		 if(size!=0){
			 System.out.println("name:" +name +":size:"+size);
//			 read(tin);
		 }
		 tarEnt = tin.getNextEntry();
		}
		tin.close();		
	}
	
	public static void main(String[] args) throws IOException{
//		targz("blacklists.tgz");
		targz("shallalist.tar.gz");
	}
}
