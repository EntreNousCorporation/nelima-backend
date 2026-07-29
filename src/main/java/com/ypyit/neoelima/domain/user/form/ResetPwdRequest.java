package com.ypyit.neoelima.domain.user.form;

import com.ypyit.neoelima.domain.user.entity.UserEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPwdRequest {

    private UserEntity user;
    private long numberOfMilliSeconds;
}
