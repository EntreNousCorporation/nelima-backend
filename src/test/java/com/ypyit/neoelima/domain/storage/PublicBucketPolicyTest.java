package com.ypyit.neoelima.domain.storage;

import com.ypyit.neoelima.domain.utils.StorageUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** La politique des buckets publics ne laisse aux anonymes que la lecture d'un objet. */
class PublicBucketPolicyTest {

    @Test
    @DisplayName("un anonyme lit un fichier public, mais ne liste, ne dépose ni ne supprime rien")
    void anonymousCanOnlyRead() {
        String policy = String.format(StorageUtils.loadPolicy().orElseThrow(), "ecole-x");
        assertThat(policy).contains("s3:GetObject").contains("arn:aws:s3:::ecole-x/*")
                .doesNotContain("s3:PutObject").doesNotContain("s3:DeleteObject").doesNotContain("s3:ListBucket")
                .doesNotContain("%s");
    }
}
