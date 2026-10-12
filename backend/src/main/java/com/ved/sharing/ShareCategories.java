package com.ved.sharing;

import java.util.List;
import java.util.Optional;

import com.ved.sharing.SharingResponses.Category;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The fixed list of subjects a share is filed under (seeded by migration; add rows to add categories). */
@Component
class ShareCategories {

    private final JdbcTemplate jdbc;

    ShareCategories(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<Category> all() {
        return jdbc.query("SELECT slug, name, emoji FROM share_categories ORDER BY position",
                (rs, i) -> new Category(rs.getString(1), rs.getString(2), rs.getString(3)));
    }

    Optional<Category> find(String slug) {
        return all().stream().filter(c -> c.slug().equals(slug)).findFirst();
    }
}
