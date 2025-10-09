package com.xprobe.scanner.active.gap;

import java.util.*;
import java.util.stream.Collectors;

/**
 * GAP风格的过滤配置
 * 参考GAP.py的过滤机制和配置选项
 * 
 * GAP.py: https://github.com/xnl-h4ck3r/GAP-Burp-Extension
 */
public class GapFilterConfig {
    
    // GAP.py的DEFAULT_STOP_WORDS（完整版）
    private static final String DEFAULT_STOP_WORDS = 
        "a,aboard,about,above,across,after,afterwards,again,against,all,almost,alone,along,already,also,although,always,am,amid,among,amongst,an,and,another,any,anyhow,anyone,anything,anyway,anywhere,are,around,as,at,back,be,became,because,become,becomes,becoming,been,before,beforehand,behind,being,below,beneath,beside,besides,between,beyond,both,bottom,but,by,can,cannot,cant,con,concerning,considering,could,couldnt,cry,de,describe,despite,do,done,down,due,during,each,eg,eight,either,eleven,else,elsewhere,empty,enough,etc,even,ever,every,everyone,everything,everywhere,except,few,fifteen,fifty,fill,find,fire,first,five,for,former,formerly,forty,found,four,from,full,further,get,give,go,had,has,hasnt,have,he,hence,her,here,hereafter,hereby,herein,hereupon,hers,herself,him,himself,his,how,however,hundred,i,ie,if,in,inc,indeed,inside,interest,into,is,it,its,itself,keep,last,latter,latterly,least,less,like,ltd,made,many,may,me,meanwhile,might,mill,mine,more,moreover,most,mostly,move,much,must,my,myself,name,namely,neither,never,nevertheless,next,nine,no,nobody,none,noone,nor,not,nothing,now,nowhere,of,off,often,on,once,one,only,onto,or,other,others,otherwise,our,ours,ourselves,out,over,own,part,per,perhaps,please,put,quite,rather,re,really,regarding,same,say,see,seem,seemed,seeming,seems,serious,several,she,should,show,side,since,sincere,six,sixty,so,some,somehow,someone,something,sometime,sometimes,somewhere,still,such,system,take,ten,than,that,the,their,them,themselves,then,thence,there,thereafter,thereby,therefore,therein,thereupon,these,they,thick,thin,third,this,those,though,three,through,throughout,thru,thus,to,together,too,top,toward,towards,twelve,twenty,two,un,under,until,up,upon,us,used,using,various,very,via,was,we,well,were,what,whatever,when,whence,whenever,where,whereafter,whereas,whereby,wherein,whereupon,wherever,whether,which,while,whither,who,whoever,whole,whom,whose,why,will,with,within,without,would,yet,you,your,yours,yourself,yourselves,zero";
    
    // 额外的技术相关停用词
    private static final String TECH_STOP_WORDS = 
        "div,span,button,input,form,table,header,footer,nav,section,article,aside,main," +
        "data,error,result,response,request,callback,handler,event,element,item,items," +
        "loading,loaded,pending,success,fail,failed,active,disabled,enabled,visible,hidden," +
        "open,close,closed,true,false,null,undefined,nan," +
        "document,window,console,object,array,string,number,boolean";
    
    private Set<String> stopWords;
    
    // 词长度限制（参考GAP.py）
    private int minWordLength = 3;      // 默认3
    private int maxWordLength = 50;     // 默认50
    
    // 配置选项（参考GAP.py）
    private boolean includeWordsWithDigits = true;   // 是否包含带数字的词
    private boolean toLowerCase = true;              // 是否转小写
    
    /**
     * 构造函数 - 加载默认停用词
     */
    public GapFilterConfig() {
        this.stopWords = new HashSet<>();
        loadDefaultStopWords();
    }
    
    /**
     * 加载GAP.py的默认停用词
     */
    private void loadDefaultStopWords() {
        // 加载GAP.py的默认停用词
        Arrays.stream(DEFAULT_STOP_WORDS.split(","))
              .map(String::trim)
              .map(String::toLowerCase)
              .forEach(stopWords::add);
        
        // 加载技术相关停用词
        Arrays.stream(TECH_STOP_WORDS.split(","))
              .map(String::trim)
              .map(String::toLowerCase)
              .forEach(stopWords::add);
    }
    
    /**
     * 添加自定义停用词
     */
    public void addCustomStopWords(String... words) {
        for (String word : words) {
            if (word != null && !word.isEmpty()) {
                stopWords.add(word.toLowerCase().trim());
            }
        }
    }
    
    /**
     * 添加自定义停用词（从逗号分隔字符串）
     */
    public void addCustomStopWords(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isEmpty()) {
            return;
        }
        
        Arrays.stream(commaSeparated.split(","))
              .map(String::trim)
              .filter(s -> !s.isEmpty())
              .map(String::toLowerCase)
              .forEach(stopWords::add);
    }
    
    /**
     * 重置为默认停用词
     */
    public void resetToDefaults() {
        stopWords.clear();
        loadDefaultStopWords();
        minWordLength = 3;
        maxWordLength = 50;
        includeWordsWithDigits = true;
        toLowerCase = true;
    }
    
    /**
     * 检查是否是停用词
     */
    public boolean isStopWord(String word) {
        if (word == null || word.isEmpty()) {
            return true;
        }
        return stopWords.contains(word.toLowerCase());
    }
    
    // ========== Getters and Setters ==========
    
    public Set<String> getStopWords() {
        return new HashSet<>(stopWords);
    }
    
    public int getMinWordLength() {
        return minWordLength;
    }
    
    public void setMinWordLength(int min) {
        this.minWordLength = Math.max(3, min);  // 至少3字符
    }
    
    public int getMaxWordLength() {
        return maxWordLength;
    }
    
    public void setMaxWordLength(int max) {
        this.maxWordLength = Math.max(minWordLength, max);
    }
    
    public boolean isIncludeWordsWithDigits() {
        return includeWordsWithDigits;
    }
    
    public void setIncludeWordsWithDigits(boolean include) {
        this.includeWordsWithDigits = include;
    }
    
    public boolean isToLowerCase() {
        return toLowerCase;
    }
    
    public void setToLowerCase(boolean lower) {
        this.toLowerCase = lower;
    }
    
    /**
     * 获取停用词的逗号分隔字符串（用于UI显示）
     */
    public String getStopWordsAsString() {
        return String.join(",", stopWords.stream()
                                         .sorted()
                                         .collect(Collectors.toList()));
    }
}


