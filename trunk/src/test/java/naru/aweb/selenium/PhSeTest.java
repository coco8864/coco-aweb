package naru.aweb.selenium;

import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class PhSeTest {
	private static final String PROXY_PAC = "http://127.0.0.1:1280/proxy.pac";
	
	@Test
	public void test1() throws InterruptedException{
//    	WebDriver driver=SeleniumUtil.ie(null);
    	WebDriver driver=SeleniumUtil.htmlunit(PROXY_PAC);
    	driver.get("https://ph.ph-sample.appspot.com");
        WebElement elm=SeleniumUtil.waitForTagText(driver, "h1", "Phatom Proxy Login(digest)");
    	SeleniumUtil.executeScript(driver,"jQuery('#username').val('admin');jQuery('#password').val('admin');jQuery('#dummyForm').submit();");
        elm=SeleniumUtil.waitForTagText(driver, "h1", "Phantom Proxy Test");
//    	Thread.sleep(1000);
    	driver.get("http://ph.ph-sample.appspot.com");
        elm=SeleniumUtil.waitForTagText(driver, "h1", "Phantom Proxy Test");
//    	Thread.sleep(1000);
        driver.get("http://127.0.0.1:1280/admin");
        elm=SeleniumUtil.waitForTagText(driver, "h1", "ìÆçÏèÛãµ");
        /*
        WebElement elm=SeleniumUtil.tagInText(driver, "h1", "ìÆçÏèÛãµ");
        */
        driver.findElement(By.linkText("setting")).click();
        elm=SeleniumUtil.waitForTagText(driver, "h2", "+AccessLogsëÄçÏ");
        elm.click();
        elm=SeleniumUtil.waitForLocator(driver, By.cssSelector("input[value='à⁄èo']"));
        elm.click();
        
        driver.findElement(By.linkText("mapping")).click();
        driver.findElement(By.linkText("accessLog")).click();
        driver.findElement(By.linkText("trace")).click();
        driver.findElement(By.linkText("stress")).click();
        driver.findElement(By.linkText("user")).click();
        driver.findElement(By.linkText("chat")).click();

        // Check the title of the page
        Thread.sleep(1000);
        System.out.println("Page title is: " + driver.getTitle());
        //Close the browser
        driver.quit();
	}

}
