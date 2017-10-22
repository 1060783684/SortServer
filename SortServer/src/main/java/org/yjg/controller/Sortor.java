package org.yjg.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.http.HttpRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.yjg.pojo.Info;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by wangwei on 17-10-20.
 */
@Controller
@RequestMapping("/sortor")
public class Sortor {
    private static final String EMPTY = "null";
    private static Map<Integer,String> addrMap;//服务器号码=>服务器主机和地址（不用socket是为了保证并发）
    private static Map<String,AtomicInteger> numberMap;//sessionId=>上传数据次数
    private static Map<String,Integer> topMap;//sessionId=>需要取前多少个数

    static{
        addrMap = new ConcurrentHashMap<Integer, String>();
        numberMap = new ConcurrentHashMap<String, AtomicInteger>();
        topMap = new ConcurrentHashMap<String, Integer>();
    }

    @PostConstruct
    public void init(){
                BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(new FileInputStream("./Sortor.conf")));
            StringBuilder conf = new StringBuilder();
            String data = "";
            while ((data = in.readLine())!= null){
                conf.append(data);
            }
            String[] datas = conf.toString().split("=");
            JSONObject jsonObject = JSON.parseObject(datas[1]);
            JSONArray jsonArray = jsonObject.getJSONArray("nodes");
            for(int i = 0;i < jsonArray.size();i++){
                JSONObject jObject = jsonArray.getJSONObject(i);
                addrMap.put(i,jObject.getString("host")+":"+jObject.getInteger("port"));
            }

        } catch (FileNotFoundException e) {//测试用,配置文件读取不到时默认的sort节点地址
            System.out.println("Sortor.conf not found");
            addrMap.put(1,"127.0.0.1:8787");
            addrMap.put(2,"127.0.0.1:8989");
            addrMap.put(3,"127.0.0.1:9090");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(in != null){
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @ResponseBody
    @RequestMapping(value = "/info",method = RequestMethod.POST)
    public String start(@RequestBody Info info){
        String sessionId = getSessionId();
        topMap.put(sessionId,info.getRecvNumber());
        numberMap.put(getSessionId(),new AtomicInteger(0));
        return sessionId;
    }

    @ResponseBody
    @RequestMapping(value = "/nextdata",method = RequestMethod.POST)
    public String nextData(HttpServletRequest request){
        BufferedWriter out = null;
        BufferedReader in = null;
        StringBuilder data = new StringBuilder();
        StringBuilder tempData = new StringBuilder();
        try {
            in = new BufferedReader(new InputStreamReader(request.getInputStream()));
            int n = 0;
            String sessionId = request.getHeader("sessionId");
            if(sessionId != null) {//添加sessionId
                tempData.append(sessionId).append("|");
                AtomicInteger atomicInteger = numberMap.get(sessionId);
                n = atomicInteger.incrementAndGet();
            }else {
                return null;
            }

            String temp = "";
            if((temp = in.readLine()) != null){//我们的一个json只有一个一行
                String json = JSONObject.parse(temp).toString();
                tempData.append(json.substring(1,json.length()-1));
            }
            tempData.append("|").append(topMap.get(sessionId));

            //set|data length|sessionId|array|top number
            data.append("set").append("|").append(tempData.length()).append("|").append(tempData).append("\n");
            Socket socket = hashSocket(n);
            String recvData = sendDataAndRecv(data.toString(),socket);
            return recvData;

        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(in != null){
                try {in.close();} catch (IOException e) {e.printStackTrace();}
            }
        }
        return null;
    }

    @ResponseBody
    @RequestMapping(value = "/datas",method = RequestMethod.GET)
    public String endData(HttpServletRequest request){
        StringBuilder data = new StringBuilder();
        if(request.getHeader("sessionId") == null){
            return null;
        }
        String sessionId = request.getHeader("sessionId");
        data.append("get").append("|").append(sessionId.length()).append("|").append(sessionId);
        int numner = 0;

        float[][] floats = new float[addrMap.size()][];
        int n = 0;
        int maxIdex = -1;
        for(Map.Entry<Integer,String> entry:addrMap.entrySet()){
            String[] hp = entry.getValue().split(":");
            Socket socket = null;
            try {
                socket = new Socket(hp[0],Integer.parseInt(hp[0]));
                String recvData = sendDataAndRecv(data.toString(),socket);
                if(!EMPTY.equals(recvData)){
                    String[] strings = recvData.split(",");
                    floats[n] = new float[strings.length];
                    for (int i = 0;i < strings.length;i++){
                        floats[n][i] = Float.parseFloat(strings[i]);
                    }
                    if(maxIdex == -1){
                        maxIdex = n;
                    }else if(floats[n][0] > floats[maxIdex][0]){
                        maxIdex = n;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        for(int i = 0;i < floats.length;i++){//选出最大的几个
            if(floats[i] != null)
                mergeHeap(floats[maxIdex],floats[i]);
        }

        for (int i = 0,count = floats.length;i < floats[maxIdex].length;i++,count--){
            float temp = floats[maxIdex][0];
            floats[maxIdex][0] = floats[maxIdex][count-1];
            floats[maxIdex][count-1] = temp;
            perDown(floats[maxIdex],0,count);
        }
        StringBuilder stringBuilder = new StringBuilder();
        for(int i = 0;i < floats[maxIdex].length;i++){
            stringBuilder.append(floats[maxIdex][i]).append(",");
        }
        stringBuilder.deleteCharAt(stringBuilder.length()-1);
        return stringBuilder.toString();
    }

    private String sendDataAndRecv(String data,Socket socket){

        BufferedWriter out  = null;
        BufferedReader in = null;
        String recvData = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.write(data);
            out.flush();
            recvData = in.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(in != null){
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if(out != null){
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return recvData;
        }
    }

    private Socket hashSocket(int number) {//使用hashmod取出对应的node的socket
        String host = addrMap.get(number%addrMap.size());
        String[] hp = host.split(":");
        Socket socket = null;
        try {
            socket = new Socket(hp[0],Integer.parseInt(hp[1]));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return socket;
    }

    private void mergeHeap(float[] oldHeap,float[] newHeap){
        for(int i = 0;i < newHeap.length;i++){
            if(oldHeap[0] < newHeap[i]){
                oldHeap[0] = newHeap[i];
                perDown(oldHeap,0,oldHeap.length);
            }
        }
    }

    private void perDown(float[] heap,int i,int count){
        int max = -1;
        if(i*2+1 < count && heap[i] < heap[i*2+1])
            max = i*2+1;
        else
            max = i;

        if(i*2+2 < count && heap[max] < heap[i*2+2])
            max = i*2+2;
        if(i != max){
            float temp = heap[i];
            heap[i] = heap[max];
            heap[max] = temp;
            perDown(heap,max,count);
        }
    }

    public String getSessionId(){
        return UUID.randomUUID().toString();
    }
}
