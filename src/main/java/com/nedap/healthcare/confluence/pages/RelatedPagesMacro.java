package com.nedap.healthcare.confluence.pages;

import com.atlassian.confluence.content.render.xhtml.ConversionContext;
import com.atlassian.confluence.core.ContentEntityObject;
import com.atlassian.confluence.core.PartialList;
import com.atlassian.confluence.labels.Label;
import com.atlassian.confluence.labels.LabelManager;
import com.atlassian.confluence.macro.Macro;
import com.atlassian.confluence.macro.MacroExecutionException;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;

@Scanned
public class RelatedPagesMacro implements Macro {

    public static final int DEFAULT_RELATED_PAGES_LIMIT = 10;
    private final LabelManager labelManager;
    private final SpaceManager spaceManager;

    @Autowired
    public RelatedPagesMacro(@ComponentImport LabelManager labelManager, @ComponentImport SpaceManager spaceManager) {
        this.labelManager = labelManager;
        this.spaceManager = spaceManager;
    }

    @Override
    public String execute(Map<String, String> map, String s, ConversionContext conversionContext) throws MacroExecutionException {
        ContentEntityObject baseContent = conversionContext.getPageContext().getEntity();

        List<Label> baseLabels = baseContent.getLabels();

        List<ContentEntityObject> result = baseLabels.stream()
                .map(label -> labelManager.getContentForLabel(0, Integer.MAX_VALUE, label))
                .map(PartialList::getList)
                .flatMap(Collection::stream)
                .distinct()
                .filter(content -> !content.equals(baseContent))
                .sorted(new ContentEntityObjectComparator(baseLabels))
                .limit(DEFAULT_RELATED_PAGES_LIMIT)
                .collect(Collectors.toList());

        StringBuilder builder = new StringBuilder();
        if(result.isEmpty()) {
            builder.append("<p><i>No related pages found.</i></p>");
        } else {
            /**
             * For some reason the CSS added to the proper resource files doesn't load in the page, so for now
             * the styling is added to the HTML build-up below.
             */
            builder.append("<ul style=\"margin: 10px 0; padding: 0; list-style: none;\">");

            for (ContentEntityObject content : result) {
                builder.append("<li>");
                builder.append("<div style=\"float: left;\"><span class=\"icon aui-icon content-type-page\" title=\"Page\">Page:</span></div>");

                // Page link
                builder.append("<div style=\"padding-left: 21px;\">")
                        .append("<a href=\"")
                        .append(content.getUrlPath())
                        .append("\">")
                        .append(content.getTitle())
                        .append("</a>")
                        .append(" <span class=\"smalltext\">(")
                        .append(getSpaceFor(content).getName())
                        .append(")</span>")
                        .append("</div>");

                // Page labels
                builder.append("<div class=\"label-details\" style=\"padding-left: 21px;\">")
                        .append("<ul class=\"label-list\">");
                for (Label label : content.getLabels()) {
                    builder.append("<li class=\"aui-label\" data-label-id=\"")
                            .append(label.getId()).append("\">")
                            .append("<a class=\"aui-label-split-main\" href=\"")
                            .append(label.getUrlPath()).append("\" rel=\"tag\">")
                            .append(label.getName())
                            .append("</a>")
                            .append("</li>");
                }
                builder.append("</ul>");
                builder.append("</div>");
                builder.append("</li>");
            }

            builder.append("</ul>");
        }
        return builder.toString();
    }

    private Space getSpaceFor(ContentEntityObject content) {
        String spaceKey = spaceManager.getSpaceFromPageId(content.getId());
        return spaceManager.getSpace(spaceKey);
    }

    @Override
    public BodyType getBodyType() {
        return BodyType.NONE;
    }

    @Override
    public OutputType getOutputType() {
        return OutputType.BLOCK;
    }

    private class ContentEntityObjectComparator implements Comparator<ContentEntityObject> {

        private final List<Label> baseLabels;

        public ContentEntityObjectComparator(List<Label> baseLabels) {
            this.baseLabels = baseLabels;
        }

        @Override
        public int compare(ContentEntityObject content1, ContentEntityObject content2) {
            long matchingLabels1 = countMatchingLabels(content1);
            long matchingLabels2 = countMatchingLabels(content2);

            // Pages with the most similar labels go first
            if (matchingLabels1 != matchingLabels2) {
                return (int) (matchingLabels2 - matchingLabels1);
            }

            int labelOccurrences1 = countOccurrencesMatchingLabels(content1);
            int labelOccurrences2 = countOccurrencesMatchingLabels(content2);

            // Pages with labels that occur more often go first
            if(labelOccurrences1 != labelOccurrences2) {
                return labelOccurrences2 - labelOccurrences1;
            }

            // Alphabetic sort on name
            return content1.getTitle().compareTo(content2.getTitle());
        }

        private int countOccurrencesMatchingLabels(ContentEntityObject content) {
            return content.getLabels().stream()
                    .filter(baseLabels::contains)
                    .map(labelManager::getContentCount)
                    .mapToInt(Integer::intValue)
                    .sum();
        }

        private long countMatchingLabels(ContentEntityObject content) {
            return content.getLabels().stream()
                    .filter(baseLabels::contains)
                    .count();
        }
    }
}
