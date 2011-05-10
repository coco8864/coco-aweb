package naru.selenium;

import java.util.List;

import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public class PhSeTest {
	private static final String PROXY_PAC = "http://127.0.0.1:1280/proxy.pac";
	
	@Test
	public void test1() throws InterruptedException{
    	WebDriver driver=SeleniumUtil.firefox(PROXY_PAC);
    	driver.get("https://ph.ph-sample.appspot.com");
    	SeleniumUtil.executeScript(driver,"jQuery('#username').val('admin');jQuery('#password').val('admin');;jQuery('#dummyForm').submit();");
    	Thread.sleep(1000);
    	driver.get("https://ph.ph-sample.appspot.com/images/11.jpg");
    	Thread.sleep(1000);
        driver.get("http://127.0.0.1:1280/admin");

        driver.findElement(By.linkText("setting")).click();
        WebElement elm=SeleniumUtil.tagInText(driver, "h1", "ìÆçÏèÛãµ");
        elm=SeleniumUtil.tagInText(driver, "h2", "+AccessLogsëÄçÏ");
        elm.click();
        WebElement e=driver.findElement(By.cssSelector("input[value='à⁄èo']"));
        e.click();
        
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
