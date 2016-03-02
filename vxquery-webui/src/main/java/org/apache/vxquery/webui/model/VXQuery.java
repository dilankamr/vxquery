package org.apache.vxquery.webui.model;/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.hyracks.api.client.HyracksConnection;
import org.apache.hyracks.api.client.IHyracksClientConnection;
import org.apache.hyracks.api.client.NodeControllerInfo;
import org.apache.hyracks.api.comm.IFrame;
import org.apache.hyracks.api.comm.IFrameTupleAccessor;
import org.apache.hyracks.api.comm.VSizeFrame;
import org.apache.hyracks.api.dataset.IHyracksDataset;
import org.apache.hyracks.api.dataset.IHyracksDatasetReader;
import org.apache.hyracks.api.dataset.ResultSetId;
import org.apache.hyracks.api.job.JobFlag;
import org.apache.hyracks.api.job.JobId;
import org.apache.hyracks.api.job.JobSpecification;
import org.apache.hyracks.client.dataset.HyracksDataset;
import org.apache.hyracks.control.cc.ClusterControllerService;
import org.apache.hyracks.control.common.controllers.CCConfig;
import org.apache.hyracks.control.common.controllers.NCConfig;
import org.apache.hyracks.control.nc.NodeControllerService;
import org.apache.hyracks.control.nc.resources.memory.FrameManager;
import org.apache.hyracks.dataflow.common.comm.io.ResultFrameTupleAccessor;
import org.apache.vxquery.compiler.CompilerControlBlock;
import org.apache.vxquery.compiler.algebricks.VXQueryGlobalDataFactory;
import org.apache.vxquery.context.DynamicContext;
import org.apache.vxquery.context.DynamicContextImpl;
import org.apache.vxquery.context.RootStaticContextImpl;
import org.apache.vxquery.context.StaticContextImpl;
import org.apache.vxquery.exceptions.SystemException;
import org.apache.vxquery.result.ResultUtils;
import org.apache.vxquery.xmlquery.query.Module;
import org.apache.vxquery.xmlquery.query.VXQueryCompilationListener;
import org.apache.vxquery.xmlquery.query.XMLQueryCompiler;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
//import org.apache.vxquery.webui.model.CmdLineOptions;

public class VXQuery {
    private static final CmdLineOptions opts = new CmdLineOptions();

    private ClusterControllerService cc;
    private NodeControllerService[] ncs;
    private IHyracksClientConnection hcc;
    private IHyracksDataset hds;

    private ResultSetId resultSetId;
    private static List<String> timingMessages = new ArrayList<String>();
    private static long sumTiming;
    private static long sumSquaredTiming;
    private static long minTiming = Long.MAX_VALUE;
    private static long maxTiming = Long.MIN_VALUE;

    public VXQuery() {
//        this.opts = opts;
    }

    public static void main(String[] args) throws Exception {
        VXQuery vxq = new VXQuery();
        String result = "";
        String query = "doc('books.xml')/bookstore/book/title";

		try {
            result = vxq.run(query);
		} catch (Exception e) {
			result = e.toString();
		}

        System.out.println(result);
    }

    public String run(String query) throws Exception {

//        CmdLineOptions opts = new CmdLineOptions();
        CmdLineParser parser = new CmdLineParser(opts);

        parser.parseArgument("*");

        VXQuery vxq = new VXQuery();
        String finalResult = vxq.execute(query);

        return finalResult;
    }

    /**
     * Creates a new Hyracks connection with: the client IP address and port provided, if IP address is provided in command line. Otherwise create a new virtual
     * cluster with Hyracks nodes. Queries passed are run either way. After running queries, if a virtual cluster has been created, it is shut down.
     *
     * @throws Exception
     */
    private String execute(String queryToExecute) throws Exception {

        String result;
        if (opts.clientNetIpAddress != null) {
            hcc = new HyracksConnection(opts.clientNetIpAddress, opts.clientNetPort);
            result = runQueries(queryToExecute);
        } else {
            if (!opts.compileOnly) {
                startLocalHyracks();
            }
            try {
                result = runQueries(queryToExecute);
            } finally {
                if (!opts.compileOnly) {
                    stopLocalHyracks();
                }
            }
        }

        return result;
    }

    private String runQueries(String queryToExecute) throws IOException, SystemException, Exception {


        String qStr = queryToExecute;

        VXQueryCompilationListener listener = new VXQueryCompilationListener(opts.showAST, opts.showTET,
                opts.showOET, opts.showRP);

        XMLQueryCompiler compiler = new XMLQueryCompiler(listener, getNodeList(), opts.frameSize,
                opts.availableProcessors, opts.joinHashSize, opts.maximumDataSize);
        resultSetId = createResultSetId();
        CompilerControlBlock ccb = new CompilerControlBlock(new StaticContextImpl(RootStaticContextImpl.INSTANCE),
                resultSetId, null);
        compiler.compile(opts.arguments.get(0), new StringReader(qStr), ccb, opts.optimizationLevel);

        Module module = compiler.getModule();
        JobSpecification js = module.getHyracksJobSpecification();

        DynamicContext dCtx = new DynamicContextImpl(module.getModuleContext());
        js.setGlobalJobDataFactory(new VXQueryGlobalDataFactory(dCtx.createFactory()));

        OutputStream resultStream = System.out;
        if (opts.resultFile != null) {
            resultStream = new FileOutputStream(new File(opts.resultFile));
        }

        PrintWriter writer = new PrintWriter(resultStream, true);

        String result = "";
        for (int i = 0; i < opts.repeatExec; ++i) {
            result = result + runJob(js, writer);
        }

        return result;
    }

    /**
     * Get cluster node configuration.
     *
     * @return Configuration of node controllers as array of Strings.
     * @throws Exception
     */
    private String[] getNodeList() throws Exception {
        Map<String, NodeControllerInfo> nodeControllerInfos = hcc.getNodeControllerInfos();
        String[] nodeList = new String[nodeControllerInfos.size()];
        int index = 0;
        for (String node : nodeControllerInfos.keySet()) {
            nodeList[index++] = node;
        }
        return nodeList;
    }

    /**
     * Creates a Hyracks dataset, if not already existing with the job frame size, and 1 reader. Allocates a new buffer of size specified in the frame of Hyracks
     * node. Creates new dataset reader with the current job ID and result set ID. Outputs the string in buffer for each frame.
     *
     * @param spec   JobSpecification object, containing frame size. Current specified job.
     * @param writer Writer for output of job.
     * @throws Exception
     */
    private String runJob(JobSpecification spec, PrintWriter writer) throws Exception {
        int nReaders = 1;
        if (hds == null) {
            hds = new HyracksDataset(hcc, spec.getFrameSize(), nReaders);
        }

        JobId jobId = hcc.startJob(spec, EnumSet.of(JobFlag.PROFILE_RUNTIME));

        FrameManager resultDisplayFrameMgr = new FrameManager(spec.getFrameSize());
        IFrame frame = new VSizeFrame(resultDisplayFrameMgr);
        IHyracksDatasetReader reader = hds.createReader(jobId, resultSetId);
        IFrameTupleAccessor frameTupleAccessor = new ResultFrameTupleAccessor();

        String result = "";
        while (reader.read(frame) > 0) {
            //int n = reader.read(frame);
            writer.print(ResultUtils.getStringFromBuffer(frame.getBuffer(), frameTupleAccessor));

            result = result + "\n" + ResultUtils.getStringFromBuffer(frame.getBuffer(), frameTupleAccessor);
            writer.flush();
            frame.getBuffer().clear();
        }

        hcc.waitForCompletion(jobId);

        return result;
    }


    protected ResultSetId createResultSetId() {
        return new ResultSetId(System.nanoTime());
    }


    public void startLocalHyracks() throws Exception {
        CCConfig ccConfig = new CCConfig();
        ccConfig.clientNetIpAddress = "127.0.0.1";
        ccConfig.clientNetPort = 39000;
        ccConfig.clusterNetIpAddress = "127.0.0.1";
        ccConfig.clusterNetPort = 39001;
        ccConfig.httpPort = 39002;
        ccConfig.profileDumpPeriod = 10000;
        cc = new ClusterControllerService(ccConfig);
        cc.start();

        ncs = new NodeControllerService[opts.localNodeControllers];
        for (int i = 0; i < ncs.length; i++) {
            NCConfig ncConfig = new NCConfig();
            ncConfig.ccHost = "localhost";
            ncConfig.ccPort = 39001;
            ncConfig.clusterNetIPAddress = "127.0.0.1";
            ncConfig.dataIPAddress = "127.0.0.1";
            ncConfig.resultIPAddress = "127.0.0.1";
            ncConfig.nodeId = "nc" + (i + 1);
            ncConfig.ioDevices = Files.createTempDirectory(ncConfig.nodeId).toString();
            ncs[i] = new NodeControllerService(ncConfig);
            ncs[i].start();
        }

        hcc = new HyracksConnection(ccConfig.clientNetIpAddress, ccConfig.clientNetPort);
    }

    public void stopLocalHyracks() throws Exception {
        for (int i = 0; i < ncs.length; i++) {
            ncs[i].stop();
        }
        cc.stop();
    }

    private static String slurp(String query) throws IOException {
        return FileUtils.readFileToString(new File(query), "UTF-8");
    }

    private static void timingMessage(String message) {
        System.out.println(message);
        timingMessages.add(message);
    }

    private static class CmdLineOptions {
        @Option(name = "-available-processors", usage = "Number of available processors. (default: java's available processors)")
        private int availableProcessors = -1;

        @Option(name = "-client-net-ip-address", usage = "IP Address of the ClusterController.")
        private String clientNetIpAddress = null;

        @Option(name = "-client-net-port", usage = "Port of the ClusterController. (default: 1098)")
        private int clientNetPort = 1098;

        @Option(name = "-local-node-controllers", usage = "Number of local node controllers. (default: 1)")
        private int localNodeControllers = 1;

        @Option(name = "-frame-size", usage = "Frame size in bytes. (default: 65,536)")
        private int frameSize = 65536;

        @Option(name = "-join-hash-size", usage = "Join hash size in bytes. (default: 67,108,864)")
        private long joinHashSize = -1;

        @Option(name = "-maximum-data-size", usage = "Maximum possible data size in bytes. (default: 150,323,855,000)")
        private long maximumDataSize = -1;

        @Option(name = "-buffer-size", usage = "Disk read buffer size in bytes.")
        private int bufferSize = -1;

        @Option(name = "-O", usage = "Optimization Level. (default: Full Optimization)")
        private int optimizationLevel = Integer.MAX_VALUE;

        @Option(name = "-showquery", usage = "Show query string.")
        private boolean showQuery;

        @Option(name = "-showast", usage = "Show abstract syntax tree.")
        private boolean showAST;

        @Option(name = "-showtet", usage = "Show translated expression tree.")
        private boolean showTET;

        @Option(name = "-showoet", usage = "Show optimized expression tree.")
        private boolean showOET;

        @Option(name = "-showrp", usage = "Show Runtime plan.")
        private boolean showRP;

        @Option(name = "-compileonly", usage = "Compile the query and stop.")
        private boolean compileOnly;

        @Option(name = "-repeatexec", usage = "Number of times to repeat execution.")
        private int repeatExec = 1;

        @Option(name = "-result-file", usage = "File path to save the query result.")
        private String resultFile = null;

        @Option(name = "-timing", usage = "Produce timing information.")
        private boolean timing;

        @Option(name = "-timing-ignore-queries", usage = "Ignore the first X number of quereies.")
        private int timingIgnoreQueries = 2;

        @Option(name = "-x", usage = "Bind an external variable")
        private Map<String, String> bindings = new HashMap<String, String>();

        @Argument
        private List<String> arguments = new ArrayList<String>();
    }

}
