package org.apache.hadoop.mapred;


import junit.framework.Assert;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.examples.SleepJob;
import org.apache.hadoop.mapreduce.JobID;
import org.apache.hadoop.mapreduce.test.system.FinishTaskControlAction;
import org.apache.hadoop.mapreduce.test.system.JTProtocol;
import org.apache.hadoop.mapreduce.test.system.JobInfo;
import org.apache.hadoop.mapreduce.test.system.MRCluster;
import org.apache.hadoop.mapreduce.test.system.TTClient;
import org.apache.hadoop.mapreduce.test.system.TaskInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestControlledJob {
  private MRCluster cluster;

  private static final Log LOG = LogFactory.getLog(TestControlledJob.class);

  public TestControlledJob() throws Exception {
    cluster = MRCluster.createCluster(new Configuration());
  }

  @Before
  public void before() throws Exception {
    cluster.setUp();
  }

  @After
  public void after() throws Exception {
    cluster.tearDown();
  }
  
  @Test
  public void testControlledJob() throws Exception {
    Configuration conf = new Configuration(cluster.getConf());
    JTProtocol wovenClient = cluster.getJTClient().getProxy();
    FinishTaskControlAction.configureControlActionForJob(conf);
    SleepJob job = new SleepJob();
    job.setConf(conf);
    
    conf = job.setupJobConf(1, 0, 100, 100, 100, 100);
    JobClient client = cluster.getJTClient().getClient();
    
    RunningJob rJob = client.submitJob(new JobConf(conf));
    JobID id = rJob.getID();
    
    JobInfo jInfo = wovenClient.getJobInfo(id);
    
    while (jInfo.getStatus().getRunState() != JobStatus.RUNNING) {
      Thread.sleep(1000);
      jInfo = wovenClient.getJobInfo(id);
    }
    
    LOG.info("Waiting till job starts running one map");
    jInfo = wovenClient.getJobInfo(id);
    Assert.assertEquals(jInfo.runningMaps(), 1);
    
    LOG.info("waiting for another cycle to " +
    		"check if the maps dont finish off");
    Thread.sleep(1000);
    jInfo = wovenClient.getJobInfo(id);
    Assert.assertEquals(jInfo.runningMaps(), 1);
    
    TaskInfo[] taskInfos = wovenClient.getTaskInfo(id);
    
    for(TaskInfo info : taskInfos) {
      LOG.info("constructing control action to signal task to finish");
      FinishTaskControlAction action = new FinishTaskControlAction(
          TaskID.downgrade(info.getTaskID()));
      for(TTClient cli : cluster.getTTClients()) {
        cli.getProxy().sendAction(action);
      }
    }
    
    jInfo = wovenClient.getJobInfo(id);
    while(!jInfo.getStatus().isJobComplete()) {
      Thread.sleep(1000);
      jInfo = wovenClient.getJobInfo(id);
    }
    
    LOG.info("Job sucessfully completed after signalling!!!!");
  }
}
