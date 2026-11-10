package cn.hengxing.inkpress.modules.article.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sensitive_word")
public class SensitiveWord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String word;

    public Long getId() { return id; }
    public String getWord() { return word; }
    public void setId(Long id) { this.id = id; }
    public void setWord(String word) { this.word = word; }
}
