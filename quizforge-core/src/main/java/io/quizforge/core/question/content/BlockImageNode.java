package io.quizforge.core.question.content;

public record BlockImageNode(String resourceId, String alt, String caption, Integer widthPercent, TextAlignment alignment) implements BlockNode {
    public BlockImageNode {
        if(widthPercent!=null && !java.util.Set.of(25,50,75,100).contains(widthPercent))
            throw new IllegalArgumentException("Image width must be 25, 50, 75 or 100 percent");
    }
    public BlockImageNode(String resourceId,String alt,String caption) { this(resourceId,alt,caption,null,null); }
}
