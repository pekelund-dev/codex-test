package dev.pekelund.pklnd.tags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.cloud.firestore.Firestore;
import dev.pekelund.pklnd.firestore.FirestoreProperties;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TagServiceTest {

    private final TagService tagService = new TagService();

    @Test
    void assignsUniqueColorsUntilPaletteExhausted() {
        for (int i = 0; i < 5; i++) {
            tagService.assignTagToEan("alice", "12345", "tag-" + i, Map.of("sv", "Tagg " + i));
        }

        List<TagView> tags = tagService.listTagOptions("alice", Locale.forLanguageTag("sv"));
        assertThat(tags).hasSize(5);
        assertThat(tags.stream().map(TagView::color)).doesNotHaveDuplicates();
    }

    @Test
    void keepsExistingColorWhenUpdatingTag() {
        tagService.assignTagToEan("alice", "12345", "tag-1", Map.of("sv", "Första"));
        String originalColor = tagService.listTagOptions("alice", Locale.ROOT).get(0).color();

        tagService.createOrUpdateTag("alice", "tag-1", Map.of("sv", "Uppdaterad"));

        List<TagView> tags = tagService.listTagOptions("alice", Locale.ROOT);
        assertThat(tags).singleElement().extracting(TagView::color).isEqualTo(originalColor);
    }

    @Test
    void generatesUniqueColorsBeyondPalette() {
        int paletteSize = 10;
        for (int i = 0; i < paletteSize + 3; i++) {
            tagService.assignTagToEan("alice", "ean-" + i, "tag-" + i, Map.of("sv", "Tagg " + i));
        }

        List<TagView> tags = tagService.listTagOptions("alice", Locale.ROOT);
        assertThat(tags).hasSize(paletteSize + 3);
        assertThat(tags.stream().map(TagView::color)).doesNotHaveDuplicates();
    }

    @Test
    void resolvesLocalizedNames() {
        tagService.assignTagToEan("alice", "111", "lokal", Map.of("sv", "Mjölk", "en", "Milk"));

        List<TagView> swedish = tagService.tagsForEan("alice", "111", Locale.forLanguageTag("sv"));
        List<TagView> english = tagService.tagsForEan("alice", "111", Locale.forLanguageTag("en"));

        assertThat(swedish).first().extracting(TagView::name).isEqualTo("Mjölk");
        assertThat(english).first().extracting(TagView::name).isEqualTo("Milk");
    }

    @Test
    void keepsMappingsPerEan() {
        tagService.assignTagToEan("alice", "222", "fresh", Map.of("sv", "Färsk"));
        tagService.assignTagToEan("alice", "333", "fryst", Map.of("sv", "Fryst"));

        assertThat(tagService.tagsForEan("alice", "222", Locale.ROOT)).hasSize(1);
        assertThat(tagService.tagsForEan("alice", "333", Locale.ROOT)).hasSize(1);
        assertThat(tagService.tagsForEan("alice", "444", Locale.ROOT)).isEmpty();
    }

    @Test
    void isolatesTagsPerUser() {
        tagService.assignTagToEan("alice", "222", "fresh", Map.of("sv", "Färsk"));
        tagService.assignTagToEan("bob", "222", "frozen", Map.of("sv", "Fryst"));

        assertThat(tagService.tagsForEan("alice", "222", Locale.ROOT))
            .singleElement()
            .extracting(TagView::id)
            .isEqualTo("fresh");
        assertThat(tagService.tagsForEan("bob", "222", Locale.ROOT))
            .singleElement()
            .extracting(TagView::id)
            .isEqualTo("frozen");
    }

    @Test
    void fallsBackToMemoryWhenFirestoreDisabled() {
        Firestore firestore = mock(Firestore.class);
        FirestoreProperties properties = new FirestoreProperties();
        properties.setEnabled(false);
        TagService service = new TagService(Optional.of(firestore), properties);

        service.assignTagToEan("alice", "ean", null, Map.of("sv", "Mjölk"));

        assertThat(service.listTagOptions("alice", Locale.forLanguageTag("sv")))
            .singleElement()
            .extracting(TagView::name)
            .isEqualTo("Mjölk");
        verifyNoInteractions(firestore);
    }
}
