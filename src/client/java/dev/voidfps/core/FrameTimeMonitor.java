package dev.voidfps.core;
import java.util.Arrays;
public final class FrameTimeMonitor {
 private static final int CAPACITY=240;
 private final double[] values=new double[CAPACITY];
 private int next,count;
 private long previous;
 public synchronized void recordFrame(long now){
  if(previous!=0){double ms=(now-previous)/1_000_000.0;if(ms>0&&ms<10000){values[next]=ms;next=(next+1)%CAPACITY;count=Math.min(count+1,CAPACITY);}}
  previous=now;
 }
 public synchronized double averageFrameTimeMs(){
  if(count==0)return 0;double sum=0;for(int i=0;i<count;i++)sum+=values[i];return sum/count;
 }
 public synchronized double percentileFrameTimeMs(double percentile){
  if(count==0)return 0;double[] sorted=Arrays.copyOf(values,count);Arrays.sort(sorted);
  int index=(int)Math.ceil(Math.max(0,Math.min(1,percentile))*sorted.length)-1;
  return sorted[Math.max(0,Math.min(sorted.length-1,index))];
 }
 public synchronized double[] snapshot(){
  double[] copy=new double[count];int start=(next-count+CAPACITY)%CAPACITY;
  for(int i=0;i<count;i++)copy[i]=values[(start+i)%CAPACITY];return copy;
 }
}
