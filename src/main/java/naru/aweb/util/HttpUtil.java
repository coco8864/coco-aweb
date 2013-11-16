package naru.aweb.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import naru.aweb.config.Config;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

public class HttpUtil {
	private static Config config=Config.getConfig();
	private static HttpClient buildHttpClient(String url,boolean isUseProxy){
		HttpClient httpClient = new DefaultHttpClient();
		if(!isUseProxy){
			return httpClient;
		}
		ServerParser proxyServer=config.findProxyServer(url);
		if(proxyServer!=null){
			String prot=null;
			if(url.startsWith("https://")){
				prot="https";
			}else{
				prot="http";
			}
			HttpHost proxy = new HttpHost(proxyServer.getHost(), proxyServer.getPort(), prot);
			httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
		}
		return httpClient;
	}
	
	public static String post(String url,Map<String,String> requestParams) throws IOException {
		HttpClient httpClient=buildHttpClient(url,true);
		HttpPost httpPost = new HttpPost(url);
		// リクエストパラメータの設定
		if(requestParams!=null){
	        List<NameValuePair> params = new ArrayList<NameValuePair>();
	        for (Map.Entry<String, String> entry : requestParams.entrySet()) {
	            params.add(new BasicNameValuePair((String) entry.getKey(), (String) entry.getValue()));
	        }		
	        httpPost.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
		}
        HttpResponse response =httpClient.execute(httpPost);
        HttpEntity httpEntity = response.getEntity();
        String restext=EntityUtils.toString(httpEntity);
        return restext;
	}
	
	public static String get(String url) throws IOException {
		return get(url,true);
	}
	
	public static String get(String url,boolean isUseProxy) throws IOException {
		HttpClient httpClient=buildHttpClient(url,isUseProxy);
		HttpGet httpGet = new HttpGet(url);
        HttpResponse response =httpClient.execute(httpGet);
        HttpEntity httpEntity = response.getEntity();
        String restext=EntityUtils.toString(httpEntity);
        return restext;
	}
}
