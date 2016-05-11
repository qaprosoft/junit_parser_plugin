package jenkins.zafira.parsers;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.lang.StringUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.qaprosoft.zafira.client.ZafiraClient;
import com.qaprosoft.zafira.client.model.JobType;
import com.qaprosoft.zafira.client.model.TestCaseType;
import com.qaprosoft.zafira.client.model.TestRunType;
import com.qaprosoft.zafira.client.model.TestRunType.Initiator;
import com.qaprosoft.zafira.client.model.TestSuiteType;
import com.qaprosoft.zafira.client.model.TestType;
import com.qaprosoft.zafira.client.model.UserType;


public class TestSuiteParser extends DefaultHandler
{	
	private static final String TEST_SUITE = "testsuite";
	private static final String TEST_CASE = "testcase";
	private static final String TEST_FAILURE = "failure";
	
	private TestSuiteType zafiraTestSuite;
	private TestCaseType zafiraTestCase;
	private TestType zafiraTest;	
	private UserType zafiraUser;
	private JobType zafiraJob;
	private TestRunType zafiraTestRun;
	private ZafiraClient zafira;
	private String pathToReport;
	private hudson.model.AbstractBuild<?,?> build;
	
	@SuppressWarnings("deprecation")
	public TestSuiteParser(String zafiraUrl, UserType user, String pathToReport, hudson.model.AbstractBuild<?,?> arg0) 
	{
		this.zafira = new ZafiraClient(zafiraUrl);
		this.pathToReport = pathToReport;
		this.build = arg0;
		if(!this.zafira.isAvailable())
		{
			throw new RuntimeException("Zafira is unavailable!!!");
		}
		this.zafiraUser = this.zafira.createUser(user).getObject();
		URI uri;
		try {
			uri = new URI(arg0.getAbsoluteUrl());
			String url = StringUtils.substringBeforeLast(arg0.getAbsoluteUrl(), "/");
			String[] items = url.split("/");
			this.zafiraJob = this.zafira.createJob(new JobType(items[items.length - 2], StringUtils.substringBeforeLast(url, "/"), 
					uri.getHost(), zafiraUser.getId())).getObject();
		} catch (URISyntaxException e) 
		{
			throw new RuntimeException("Wrong Jenkins host!!!" + e);
		}
		
	}
	
	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException 
	{
		 switch(qName)
         {
         	case TEST_SUITE: createAndSaveTestSuite(attributes); break;
         	case TEST_CASE: createAndSaveTestCase(attributes); break;
         	case TEST_FAILURE: break;
         	default: break;         		
         } 
	}
	
	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException 
	{
		 switch(qName)
         {
	     	case TEST_SUITE:
	     		this.zafira.finishTestRun(this.zafiraTestRun.getId());
	     		zafiraTestSuite = null;
	     		zafiraUser = null;
	     		zafiraTestRun = null;
	     		break;
	     	case TEST_CASE: 
	     		saveTest(); 
	     		zafiraTestCase = null;
	     		zafiraTest = null;
	     		break;
	     	case TEST_FAILURE: break;
			default: break;
         }
	}
	
	@Override
	public void characters(char ch[], int start, int length) throws SAXException 
	{
		if(zafiraTest != null)
		{
			zafiraTest.setStatus(TestType.Status.FAILED);
			zafiraTest.setMessage(new String(ch, start, length));
		}
	}
	
	private void createAndSaveTestSuite(Attributes attributes)
	{
		zafiraTestSuite = new TestSuiteType(attributes.getValue("name"), pathToReport, zafiraUser.getId());
		zafiraTestSuite = zafira.createTestSuite(zafiraTestSuite).getObject();
		
		zafiraTestRun = new TestRunType(zafiraTestSuite.getId(), zafiraUser.getId(), build.getBuildVariables().get("GIT_URL"), 
				build.getBuildVariables().get("GIT_BRANCH"), build.getBuildVariables().get("GIT_COMMIT"), "", zafiraJob.getId(), build.getNumber(), Initiator.HUMAN , "");
		zafiraTestRun = zafira.createTestRun(zafiraTestRun).getObject();
	}
	
	private void createAndSaveTestCase(Attributes attributes)
	{
		zafiraTestCase = new TestCaseType(attributes.getValue("classname"), attributes.getValue("name"), null, zafiraTestSuite.getId(), zafiraUser.getId());
		zafiraTestCase = zafira.createTestCase(zafiraTestCase).getObject();
		
		zafiraTest = new TestType();
		zafiraTest.setName(attributes.getValue("name"));
		zafiraTest.setStatus(TestType.Status.PASSED);
		zafiraTest.setTestCaseId(zafiraTestCase.getId());
		zafiraTest.setLogURL(this.build.getAbsoluteUrl() + "console");
	}
	
	private void saveTest()
	{
		zafiraTest.setTestRunId(zafiraTestRun.getId());
		zafiraTest = zafira.createTest(zafiraTest).getObject();
	}
}
