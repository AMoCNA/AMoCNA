package com.kubiki.daedalus.spring;

import com.kubiki.daedalus.annotation.Bind;
import com.kubiki.daedalus.annotation.DaedalusRepository;
import com.kubiki.daedalus.annotation.Template;

@DaedalusRepository
public interface TestRepo {
    @Template(resource = "templates/test-template.txt")
    String greet(@Bind("name") String name, @Bind("place") String place);
}
