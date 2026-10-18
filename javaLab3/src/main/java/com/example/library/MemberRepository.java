package com.example.library;

import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Repository
public class MemberRepository {
    private final Map<Long, Member> members = new ConcurrentHashMap<>();

    public void save(Member member) { members.put(member.id(), member); }
    public Optional<Member> findById(long id) { return Optional.ofNullable(members.get(id)); }
    public List<Member> findAll() { return List.copyOf(members.values()); }
}
