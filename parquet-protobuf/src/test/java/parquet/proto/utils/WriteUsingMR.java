/**
 * Copyright 2013 Lukas Nalezenec
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package parquet.proto.utils;

import com.google.protobuf.Message;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import parquet.Log;
import parquet.proto.ProtoParquetOutputFormat;
import parquet.proto.TestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.lang.Thread.sleep;

/**
 * Writes data to parquet file using MapReduce job.
 */
public class WriteUsingMR {

  private static final Log LOG = Log.getLog(WriteUsingMR.class);
  Configuration conf;
  private static List<Message> inputMessages;
  Path outputPath;

  public void setConfiguration(Configuration conf) {
    this.conf = conf;
  }

  public static class WritingMapper extends Mapper<LongWritable, Text, Void, Message> {

    public void run(Context context) throws IOException, InterruptedException {
      if (inputMessages == null || inputMessages.size() == 0) {
        throw new RuntimeException("No mock data given");
      } else {
        for (Message msg : inputMessages) {
          context.write(null, msg);
          LOG.debug("Reading msg from mock writing mapper" + msg);
        }
      }
    }
  }

  public Path write(Class<? extends Message> pbClass, Message... messages) throws Exception {

    synchronized (WriteUsingMR.class) {

      outputPath = TestUtils.someTemporaryFilePath();
      if (conf == null) conf = new Configuration();
      final Path inputPath = new Path("src/test/java/parquet/proto/ProtoInputOutputFormatTest.java");

      inputMessages = Collections.unmodifiableList(Arrays.asList(messages));

      final Job job = new Job(conf, "write");

      // input not really used
      TextInputFormat.addInputPath(job, inputPath);
      job.setInputFormatClass(TextInputFormat.class);

      job.setMapperClass(WritingMapper.class);
      job.setNumReduceTasks(0);

      job.setOutputFormatClass(ProtoParquetOutputFormat.class);
      ProtoParquetOutputFormat.setOutputPath(job, outputPath);
      ProtoParquetOutputFormat.setProtobufClass(job, pbClass);

      waitForJob(job);

      inputMessages = null;
      return outputPath;
    }
  }

  static void waitForJob(Job job) throws Exception {
    job.submit();
    while (!job.isComplete()) {
      LOG.debug("waiting for job " + job.getJobName());
      sleep(50);
    }
    LOG.debug("status for job " + job.getJobName() + ": " + (job.isSuccessful() ? "SUCCESS" : "FAILURE"));
    if (!job.isSuccessful()) {
      throw new RuntimeException("job failed " + job.getJobName());
    }
  }
}
