package org.deeplearning4j.examples.recurrent.processlottery;


import org.deeplearning4j.api.storage.StatsStorage;
 import org.deeplearning4j.nn.api.Layer;
 import org.deeplearning4j.nn.api.OptimizationAlgorithm;
 import org.deeplearning4j.nn.conf.*;
 import org.deeplearning4j.nn.conf.layers.*;
 import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
 import org.deeplearning4j.nn.weights.WeightInit;
 import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
 import org.deeplearning4j.ui.api.UIServer;
 import org.deeplearning4j.ui.stats.StatsListener;
 import org.deeplearning4j.ui.storage.InMemoryStatsStorage;
 import org.deeplearning4j.util.ModelSerializer;
 import org.nd4j.linalg.activations.Activation;
 import org.nd4j.linalg.api.ndarray.INDArray;
 import org.nd4j.linalg.dataset.DataSet;
 import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
 import org.nd4j.linalg.factory.Nd4j;
 import org.nd4j.linalg.io.ClassPathResource;
 import org.nd4j.linalg.learning.config.RmsProp;
 import org.nd4j.linalg.lossfunctions.LossFunctions;
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
 import java.io.File;
 import java.util.Random;


 /**
  * lottery rule:every day has 120 term, during 10:00-22:00(72),every 10 minutes generate new lottery number, during 22:00-02:00(48) every 5 minutes generate new lottery number,
  * lottery number rule: its length is 5 bit, every bit choose one from 0-9 digit
  * this example only try to get the lottery algorithm by an unidentified structure underlying the data,maybe the inputting features still is less,
  * although the model don't seem to get the original lottery algorithm, but this example can still be used as a reference for processing sequence data.
  * Created by wangfeng.
  */
public class TrainLotteryModel {
    private static Logger log = LoggerFactory.getLogger(TrainLotteryModel.class);


    private static int batchSize = 64;
    private static long seed = 123;
    private static int numEpochs = 1;
    private static String baseRootPath = System.getProperty("user.dir");
    private static String rootPath = baseRootPath.substring(0,baseRootPath.lastIndexOf(File.separatorChar)) + File.separatorChar + "out";
    private static String modelPath = rootPath + File.separatorChar + "models" + File.separatorChar + "lotteryPredictModel.json";

    private static boolean modelType = true;
	public static void main(String[] args) throws Exception{
        long startTime = System.currentTimeMillis();
        System.out.println(startTime);
        File modelFile = new File(modelPath);
        boolean hasFile = modelFile.exists()?true:modelFile.createNewFile();
        log.info( modelFile.getPath() );

        DataSetIterator trainIterator = new LotteryDataSetIterator(new ClassPathResource("/lottery/cqssc_train.csv").getFile().getPath(), batchSize, modelType);
        DataSetIterator testIterator = new LotteryDataSetIterator(new ClassPathResource("/lottery/cqssc_test.csv").getFile().getPath(), 2 , modelType);
        DataSetIterator validateIterator = new LotteryDataSetIterator(new ClassPathResource("/lottery/cqssc_validate.csv").getFile().getPath(), 2 , modelType);

        MultiLayerNetwork model = getNetModel(trainIterator.inputColumns(), trainIterator.totalOutcomes());
        UIServer uiServer = UIServer.getInstance();
        StatsStorage statsStorage = new InMemoryStatsStorage();
        uiServer.attach(statsStorage);
        model.setListeners(new StatsListener(statsStorage),new ScoreIterationListener(10));

        Layer[] layers = model.getLayers();
        int totalNumParams = 0;
        for( int i=0; i<layers.length; i++ ){
            int nParams = layers[i].numParams();
            System.out.println("Number of parameters in layer " + i + ": " + nParams);
            totalNumParams += nParams;
        }
        System.out.println("Total number of network parameters: " + totalNumParams);

        for (int i = 0;i < numEpochs; i ++) {
            System.out.println("=============numEpochs==========================" + i);
            model.fit(trainIterator);
        }
        long endTime = System.currentTimeMillis();
        System.out.println("=============run time=====================" + (endTime - startTime));
        ModelSerializer.writeModel(model, modelFile, true);

        int luckySize = 5;
        if (modelType) {
            while (testIterator.hasNext()) {
                DataSet ds = testIterator.next();
                //predictions all at once
                INDArray output = model.output(ds.getFeatures());

                INDArray label = ds.getLabels();
                INDArray preOutput = Nd4j.argMax(output.getRow(0), new int[]{1});
                INDArray realLabel = Nd4j.argMax(label.getRow(0), new int[]{1});
                String peLabel = "";
                String reLabel = "";
                for (int dataIndex = 0; dataIndex < 5; dataIndex ++) {
                    peLabel += preOutput.getRow(dataIndex).getInt(0);
                    reLabel += realLabel.getRow(dataIndex).getInt(0);
                }
                log.info("test-->real lottery {}  prediction {} status {}",  reLabel,peLabel, peLabel.equals(reLabel));
            }
            while (validateIterator.hasNext()) {
                DataSet ds = validateIterator.next();
                //predictions all at once
                INDArray output = model.feedForward(ds.getFeatures()).get(0);
                INDArray label = ds.getLabels();
                INDArray preOutput = Nd4j.argMax(output.getRow(0), new int[]{1});
                INDArray realLabel = Nd4j.argMax(label.getRow(0), new int[]{1});
                String peLabel = "";
                String reLabel = "";
                for (int dataIndex = 0; dataIndex < 5; dataIndex ++) {
                    peLabel += preOutput.getRow(dataIndex).getInt(0);
                    reLabel += realLabel.getRow(dataIndex).getInt(0);
                }
                log.info("validate-->real lottery {}  prediction {} status {}",  reLabel,peLabel, peLabel.equals(reLabel));
            }

            String currentLottery = "27578";
            INDArray initCondition = Nd4j.zeros(1, 5, 10);
            String[] featureAry = currentLottery.split("");
            for( int j = 0; j < featureAry.length; j ++ ){
                int p = Integer.parseInt(featureAry[j]);
                initCondition.putScalar(new int[]{0, j, p}, 1);
            }
            INDArray output1 = model.output(initCondition);
            INDArray preOutput1 = Nd4j.argMax(output1.getRow(0), new int[]{1});
            String latestLottery = "";
            for (int dataIndex = 0; dataIndex < 5; dataIndex ++) {
                latestLottery += preOutput1.getRow(dataIndex).getInt(0);
            }
            System.out.println("==prediction====result===" +  latestLottery);

        } else {


            int predictCount = 2;
            String predictDateNum = "20180716100";//20180716,100
            //Create input for initialization
            //For single time step:input has shape [miniBatchSize,inputSize] or [miniBatchSize,inputSize,1]. miniBatchSize=1 for single example.<br>
            //For multiple time steps:input has shape  [miniBatchSize,inputSize,inputTimeSeriesLength]
            INDArray initCondition = Nd4j.zeros(predictCount, 10, predictDateNum.length());

            String[] featureAry = predictDateNum.split("");
            for( int j = 0; j < featureAry.length; j ++ ){
                int p = Integer.parseInt(featureAry[j]);
                for( int i=0; i< predictCount; i++ ){
                    initCondition.putScalar(new int[]{i, p,j}, 1);
                }
            }
            StringBuilder[] sb = new StringBuilder[predictCount];
            for( int i = 0; i < predictCount; i ++ ) {
                sb[i] = new StringBuilder(predictDateNum);
            }
            //Clear the previous state of the RNN layers (if any)
            model.rnnClearPreviousState();
            INDArray output = model.rnnTimeStep(initCondition);
            //output.size(x) will get the size along a specified dimension,
            //output.tensorAlongDimension(...) will get the vector along a particular dimension
            output = output.tensorAlongDimension(output.size(2)-1,1,0);	//Gets the last time step output
            Random random = new Random(12345);
            for( int i=0; i < luckySize; i++ ){
                //Set up next input (single time step) by sampling from previous output
                INDArray nextInput = Nd4j.zeros(predictCount,10);
                //Output is a probability distribution. Sample from this for each example we want to generate, and add it to the new input
                for( int s = 0; s < predictCount; s ++ ){
                    double[] outputProbDistribution = new double[10];
                    for( int j = 0; j < outputProbDistribution.length; j++ ) {
                        outputProbDistribution[j] = output.getDouble(s,j);
                    }
                    double sum = 0.0;
                    int luckyNum = 0;
                    double d = random.nextDouble();

                    for( int j = 0; j < outputProbDistribution.length; j++ ){
                        sum += outputProbDistribution[j];
                        if( d <= sum ) luckyNum = i;
                    }
                    //Prepare next time step input
                    nextInput.putScalar(new int[]{s,luckyNum}, 1.0f);
                    sb[s].append(luckyNum);
                }
                //Do one time step of forward pass
                output = model.rnnTimeStep(nextInput);
            }
            for( int i = 0; i < predictCount; i++ ) {
                System.out.println("==prediction====result===" + predictCount + "======" + sb[i].toString());
            }
        }
    }

    //create the neural network
	public static MultiLayerNetwork getNetModel(int inputNum, int outputNum) {
        MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                .trainingWorkspaceMode(WorkspaceMode.ENABLED).inferenceWorkspaceMode(WorkspaceMode.ENABLED)
                .seed(seed)
                .optimizationAlgo( OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .weightInit(WeightInit.XAVIER)
                .updater(new RmsProp.Builder().rmsDecay(0.95).learningRate(1e-2).build())
                .list()
                .layer(0, new GravesLSTM.Builder().name("lstm1")
                        .activation(Activation.TANH).nIn(inputNum).nOut(100).build())
                .layer(1, new GravesLSTM.Builder().name("lstm2")
                        .activation(Activation.TANH).nOut(80).build())
                .layer(2, new RnnOutputLayer.Builder().name("output")
                        .activation(Activation.SOFTMAX).nOut(outputNum).lossFunction(LossFunctions.LossFunction.MSE)
                        .build())
                .pretrain(false).backprop(true)
                .build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        return net;
    }


}
