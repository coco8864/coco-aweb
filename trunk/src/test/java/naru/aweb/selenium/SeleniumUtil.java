package naru.aweb.selenium;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeExtension;
import org.openqa.selenium.chrome.ChromeProfile;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.google.common.base.Function;



public class SeleniumUtil {
	private static final long WAIT_TIMEOUT=10;

	static Function<WebDriver, WebElement> presenceOfElementLocated(final By locator) {
        return new Function<WebDriver, WebElement>() {
            public WebElement apply(WebDriver driver) {
                return driver.findElement(locator);
            }
        };
	}
	
	static Function<WebDriver, WebElement> presenceOfElementTagText(final String tag,final String text) {
        return new Function<WebDriver, WebElement>() {
            public WebElement apply(WebDriver driver) {
            	return tagInText(driver,tag,text);
            }
        };
	}
	
	public static WebElement waitForLocator(WebDriver driver,By locator){
        Wait<WebDriver> wait=new WebDriverWait(driver,WAIT_TIMEOUT);
        return wait.until(presenceOfElementLocated(locator));
	}
	public static WebElement waitForTagText(WebDriver driver,String tag,String text){
        Wait<WebDriver> wait=new WebDriverWait(driver,WAIT_TIMEOUT);
        return wait.until(presenceOfElementTagText(tag,text));
	}
	
	public static WebDriver chrome(String proxyPac){
        Proxy proxy=new Proxy();
        ChromeProfile prop=new ChromeProfile();
        if(proxyPac!=null){
            proxy.setProxyAutoconfigUrl(proxyPac);
            prop.setProxy(proxy);
        }
        WebDriver driver = new ChromeDriver(prop,new ChromeExtension());
        return driver;
	}
	
	public static WebDriver firefox(String proxyPac){
        FirefoxProfile prop=new FirefoxProfile();
        if(proxyPac!=null){
            Proxy proxy=new Proxy();
            proxy.setProxyAutoconfigUrl(proxyPac);
            prop.setProxyPreferences(proxy);
        }
    	WebDriver driver = new FirefoxDriver(prop);
    	return driver;
	}
	
	public static WebDriver htmlunit(String proxyPac){
		HtmlUnitDriver driver = new HtmlUnitDriver(true);
    	if(proxyPac!=null){
    		driver.setAutoProxy(proxyPac);
    	}
		return driver;
	}
	
	public static WebDriver ie(String proxyPac){
		if(proxyPac!=null){
			throw new RuntimeException("ie not suppoert proxyPac");
		}
		InternetExplorerDriver driver=new InternetExplorerDriver();
		return driver;
	}
	
	public static Object executeScript(WebDriver driver,String script){
		if(driver instanceof HtmlUnitDriver){
			return ((HtmlUnitDriver)driver).executeScript(script);
		}else if(driver instanceof RemoteWebDriver){
			return ((RemoteWebDriver)driver).executeScript(script);
		}
		throw new RuntimeException("driver not support javascript");
	}

	public static WebElement tagInText(WebDriver driver,String tag,String text){
		try {
			List<WebElement> elements = driver.findElements(By.cssSelector(tag));
			for(WebElement element:elements){
			    if(element.getText().indexOf(text)>=0){
			    	return element;
			    }
			}
		} catch (RuntimeException e) {
		}
        return null;
	}
}
