package com.bilixxb.GamePlayLogic;

import org.bukkit.entity.Player;

public class SkillCooldown {
    private final double maxCooldown;
    private final int usableTimes;
    private final String skillName;
    private double nowPassed;
    private int nowUsableTimes;
    private final Player player;
    public SkillCooldown(double maxCooldown,int usableTimes,Player player,String SkillName){
        this.maxCooldown=maxCooldown;
        this.usableTimes=usableTimes;
        this.player=player;
        this.skillName=SkillName;
    }

    public String getSkillName() {
        return skillName;
    }

    public double getMaxCooldown() {
        return maxCooldown;
    }

    public Player getPlayer() {
        return player;
    }

    public int getUsableTimes() {
        return usableTimes;
    }

    public double getNowPassed() {
        double result = Math.round(nowPassed * 10.0) / 10.0;
        return result;
    }
    public double getRemaining(){
        double i=maxCooldown-nowPassed;
        double result = Math.round(i * 10.0) / 10.0;
        return result;
    }

    public void setNowPassed(double nowPassed) {
        this.nowPassed = nowPassed;
    }

    public void addNowPassed(double d) {
        this.nowPassed = nowPassed+d;
    }
    public boolean generalAddNowPassed(){
        addNowPassed(0.1d);
        if(nowPassed >= maxCooldown){
            nowPassed = 0;
            generalAddNowUsableTimes();
            // 只有当可用次数达到最大可用次数时才算完全冷却完成
            if(nowUsableTimes >= usableTimes){
                nowUsableTimes = usableTimes; // 确保不超过最大值
                return true; // 完全冷却完成
            }
            return false; // 部分冷却完成（恢复了一次使用次数）
        }
        return false; // 冷却尚未完成
    }
    public double getCooldownProgress() {
        return nowPassed / maxCooldown; // 已过时间占总冷却时间的比例
    }
    public int getNowUsableTimes(){
        return nowUsableTimes;
    }
    public void addNowUsableTimes(int i){
        nowUsableTimes+=i;
    }
    public void generalAddNowUsableTimes(){
        addNowUsableTimes(1);
    }

    public void setNowUsableTimes(int nowUsableTimes) {
        this.nowUsableTimes = nowUsableTimes;
    }
}
