package io.github.shanpark.r2batis.mapper;

import io.github.shanpark.r2batis.core.MethodImpl;
import io.github.shanpark.r2batis.exception.InvalidMapperElementException;
import io.github.shanpark.r2batis.util.ReflectionUtils;
import io.github.shanpark.r2batis.util.TypeUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import ognl.Ognl;
import ognl.OgnlException;

import java.util.*;

/**
 * Mapper 인터페이스의 Method 호출이 발생하면 새로 생성되어 최종적인 SQL이 생성되는 과정에서
 * 사용되는 Context 객체이다.
 */
@Data
public class MapperContext {

    static class Branch {
        /**
         * Node에서 로컬 변수의 이름을 Unique하게 변경해야할 때 사용한다.
         * uniqueId 를 사용해서 최종 place를 만들 때는 전체 branch를 대상으로도 unique해야 한다.
         * 현제는 중첩 foreach를 위해서만 사용되고 있다.
         */
        private final String uniqueId;

        /**
         * 생성된 sql에 사용된 모든 placeholder 들을 모아 놓은 map.
         */
        private final Map<String, Class<?>> placeholderMap = new HashMap<>();

        /**
         * 최종 sql 생성 시 bind할 실제 값을 ognl을 이용해서 뽑아낼 때 필요한 객체들을 모아놓은 map.
         */
        private final Map<String, Object> paramMap = new HashMap<>();

        Branch(String uniqueId) {
            this.uniqueId = uniqueId;
        }
    }

    @RequiredArgsConstructor
    static class VarInfo {
        private final MethodImpl.ParamInfo paraminfo;
        private final Object value;
    }

    /**
     * MapperContext를 하나 생성해서 반환한다.
     * Method 호출 시 최초 MapperContext를 생성할 때 사용된다.
     *
     * @param paramInfos Method의 parameter 정보 구조체를 담은 array 객체.
     * @param args Method 호출 시 전달된 실제 argument 들을 담은 array 객체.
     * @return 생성된 MapperContext 객체.
     */
    public static MapperContext of(MethodImpl.ParamInfo[] paramInfos, Object[] args) {
        return new MapperContext(paramInfos, args);
    }

    /**
     * mapper의 method 호출 시 전달된 arguments 저장.
     * 전역변수처럼 Query의 어디서나 접근 가능하다.
     */
    private final List<VarInfo> methodArgs;

    /**
     * foreach의 item 처럼 local에서 생성되는 변수들 저장.
     * 해당 요소의 내부에서만 접근 가능한 변수들이 저장된다.
     */
    private final List<VarInfo> localVars;

    private final Stack<Branch> branchStack = new Stack<>();

    private MapperContext(MethodImpl.ParamInfo[] paramInfos, Object[] args) {
        methodArgs = new ArrayList<>();
        localVars = new ArrayList<>();
        branchStack.push(new Branch(""));

        // paramInfos 와 args는 항상 갯수가 같아야한다.
        Branch curBranch = branchStack.peek();
        for (int inx = 0; inx < paramInfos.length; inx++) {
            methodArgs.add(new VarInfo(paramInfos[inx], args[inx]));
            curBranch.paramMap.put(paramInfos[inx].getName(), args[inx]); // test 조건 같은 곳에서만 사용되는 것들이 placeholder로 검출되지 않기 기본적으로 Method의 argument는 모두 paramMap에 추가되어야 한다.
        }
    }

    public void pushLocalVar(String name, Class<?> clazz, Object value) {
        localVars.add(new VarInfo(new MethodImpl.ParamInfo(name, clazz), value));
        branchStack.peek().paramMap.put(name, value);
    }

    public void popLocalVar() {
        VarInfo varInfo = localVars.remove(localVars.size() - 1);
        branchStack.peek().paramMap.remove(varInfo.paraminfo.getName());
    }

    /**
     * 검색된 placeholder를 추가한다. 정규식을 사용하여 placeholder를 검색하고 있으며 정규식에는 공백문자를 포함하고 있지 않다.
     * 따라서 placeholder는 절대로 공백문자를 포함하지 않기 때문에 공백문자는 고려할 필요가 없다.
     *
     * @param placeholder 추가할 placeholder 문자열. placeholder 정규식에 매칭된 문자열이므로 절대로 공백문자는 없다.
     */
    public void addPlaceholder(String placeholder) {
        Branch curBranch = branchStack.peek();
        if (!curBranch.placeholderMap.containsKey(placeholder)) {
            String[] fields = placeholder.split("\\.");
            Class<?> type = getTypeByFullFields(fields); // 하위 필드까지 탐색해서 해당 값의 타입을 가져온다.
            curBranch.placeholderMap.put(placeholder, type);

            if (!curBranch.paramMap.containsKey(fields[0])) // 아직 paramMap에 없다면 추가해준다. getVarByField0() 함수가 비용이 비싸기 때문에..
                curBranch.paramMap.put(fields[0], getVarByField0(fields[0])); // paramMap에는 하위 필드값이 아닌 root 객체를 저장한다. ognl 로 읽어올 때 사용하기 때문이다.
        }
    }

    /**
     * foreach에서 새로운 로컬 변수들이 생성되기 때문에 branch가 생성된다. 이 때 호출되어 새로운 branch를 생성하는 함수이다.
     * newBranch()를 호출하는 쪽에서는 같은 레벨의 branch들 사이에서만 unique한 값을 전달하면 된다.
     * 이 함수 내에서 이전 branch까지 모두 반영하여 이전에 생성된 전체 branch들까지 모두 다해도 unique한 id값을 갖도록
     * branch를 생성해준다.
     */
    public void newBranch(String uniqueId) {
        branchStack.push(new Branch(branchStack.peek().uniqueId + uniqueId)); // 새로 생성되는 branch는 이전 branch의 uniqueId에 계속 덧붙여서 uniqueId를 생성한다.
    }

    /**
     * 새롬게 생성된 로컬에서 사용되는 변수들은 모두 최종 sql을 생성하고 bind할 때 사용되어야 하기 때문에 새로운 이름을 부여받고
     * 모두 branch의 placeholder와 paramMap에 저장되어 있는 상태이다. 이것들을 모두 이전 branch와 합쳐서 유지를 시켜줘야
     * 최종 sql 생성 시에 bind 할 수 있으므로 foreach의 처리가 끝나면 항상 호출해서 현재 branch와 이전 branch를 합쳐주고
     * 현재 branch를 pop 시켜주어야 한다.
     */
    public void mergeBranch() {
        Branch lastBranch = branchStack.pop();
        Branch curBranch = branchStack.peek();

        // local 변수용 branch는 foreach 에서만 생성되는데 모든 foreach 노드는 각자의 고유한 uid를 갖고 있고
        // 새로 생성되는 placeholder는 이 uid 값을 포함하므로 절대로 이전 branch와 같은 이름의 변수는 생성될 수 없다.
        // 따라서 merge할 때 이전 branch와 이름 중복은 걱정할 필요 없이 모두 put하면 된다.
        curBranch.placeholderMap.putAll(lastBranch.placeholderMap);
        curBranch.paramMap.putAll(lastBranch.paramMap);
    }

    public String getUniqueId() {
        return branchStack.peek().uniqueId;
    }

    /**
     * 현재 모든 branch에서 유효한 paramMap 들을 모두 모아서 반환한다.
     * sql을 실행하기 직전에 placeholder에 대응되는 실제 parameter 값을 생성된 sql에 bind할 때와 If에서 test문 검사할 때 사용되는데
     * test 조건을 검사할 때만 branch가 여러 개일 수 있고 그 경우에만 새로운 map이 생성돼서 반환된다.
     *
     * @return 모든 branch의 paramMap들의 값들을 하나의 Map으로 만들어서 반환.
     */
    public Map<String, Object> getParamMap() {
        if (branchStack.size() == 1) {
            return branchStack.peek().paramMap; // branch가 하나밖에 없으면 따로 합치는 작업은 필요없다.
        } else {
            Map<String, Object> newParamMap = new HashMap<>();
            branchStack.forEach(branch -> newParamMap.putAll(branch.paramMap)); // 뒤에 있는 paramMap이 우선 순위가 높다.
            return newParamMap;
        }
    }

    /**
     * bind가 필요한 placeholder 들의 set을 반환한다.
     * sql을 실행하기 직전에 bind를 수행하기 때문에 SQL 생성은 이미 끝난 상태에서만 호출되어야 한다.
     * 그 외의 시점에 호출이 된다면 정상 동작을 보장할 수 없다.
     *
     * @return bind가 필요한 placeholder 들의 set
     */
    public Set<String> getBindSet() {
        // 항상 generation이 끝난 후에 호출되므로 branchStack에는 1건만 있어야 한다.
        return branchStack.peek().placeholderMap.keySet();
    }

    /**
     * bind할 placeholder의 타입을 반환한다.
     * sql을 실행하기 직전에 bind를 수행하기 때문에 SQL 생성은 이미 끝난 상태에서만 호출되어야 한다.
     * 그 외의 시점에 호출이 된다면 정상 동작을 보장할 수 없다.
     *
     * @param placeholder 타입을 조회할 placeholder.
     * @return 조회된 placeholder의 타입. Class 객체이다.
     */
    public Class<?> getPlaceholderType(String placeholder) {
        // 항상 generation이 끝난 후에 호출되므로 branchStack에는 1건만 있어야 한다.
        return branchStack.peek().placeholderMap.get(placeholder);
    }

    /**
     * foreach의 item, index 속성으로 지정된 placeholder를 새로 부연된 이름으로 변경할 때 사용된다.
     * item, index 속성에는 당연히 multi depth의 이름이 지정될 수 없으므로 placeholder와 newPlaceholder는
     * 하나의 field로 이루어진 값이라고 할 수 있다.
     *
     * @param placeholder 변경 대상이 되는 placeholder 이름.
     * @param newPlaceholder placeholder로 지정된 값의 새로운 이름. 이 이름으로 변경된다.
     */
    public void expandPlaceholder(String placeholder, String newPlaceholder) {
        Branch curBranch = branchStack.peek(); // 현재 작업 branch에서만 expand 해야 한다.

        // 기존에 placeholderMap에서 placeholder라는 이름으로 생성된 것들을 newPlaceholder로 변경한다.
        Map<String, Class<?>> newPlaceholderMap = new HashMap<>();
        Iterator<Map.Entry<String, Class<?>>> it = curBranch.placeholderMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Class<?>> entry = it.next();
            String key = entry.getKey();

            // 사용된 placeholder를 newplaceholder로 바꾼다.
            if (key.equals(placeholder) || key.startsWith(placeholder + ".")) {
                newPlaceholderMap.put(newPlaceholder + key.substring(placeholder.length()), entry.getValue());
                it.remove();
            }
        }
        curBranch.placeholderMap.putAll(newPlaceholderMap);

        Object value = curBranch.paramMap.remove(placeholder); // placeholder는 paramMap이 반드시 있어야 하고 그 값이 null일 수는 있다.
        curBranch.paramMap.put(newPlaceholder, value);         // 따라서 null 여부에 상관없이 항상 put해줘야 한다.
    }

    /**
     * first field 만으로 변수를 찾아서 반환하는 함수이다.
     * 먼저 해당 이름의 변수를 local var 중에서 찾고 없으면 method args에서 찾고 그래도 없으면
     * 한 개의 pojo만 arg로 전달된 경우에 한하여 arg[0] 객체의 필드 중에서 같은 이름의 필드를 찾아서 반환한다.
     *
     * @param field0 어떤 변수를 참조하는 전체 문자열에서 첫 번째 필드 문자열을 전달해야 한다. 즉 "obj.field1.field2" 이면 obj만 전달한다.
     * @return 검색 후 찾은 값 객체
     */
    public Object getVarByField0(String field0) {
        // localVars는 스택과 같다. 뒤에 추가된 변수가 우선순위가 더 높으므로 뒤에서부터 검색한다.
        VarInfo varInfo = null;
        for (int inx = localVars.size() - 1; inx >= 0; inx--) { // 뒤에 붙은 param들이 우선순위가 더 높기때문에 반드시 뒤에서부터 검색.
            VarInfo localVar = localVars.get(inx);
            if (field0.equals(localVar.paraminfo.getName())) {
                varInfo = localVar;
                break;
            }
        }

        if (varInfo != null) { // localVars에서 찾았다.
            return varInfo.value;
        } else { // localVars에 없으면 methodArgs에서 찾는다.
            Optional<VarInfo> methodArg = methodArgs.stream().filter(pair -> field0.equals(pair.paraminfo.getName())).findFirst();
            if (methodArg.isPresent()) {
                return methodArg.get().value;
            } else { // methodArgs 에서도 없으면
                if (methodArgs.size() == 1) { // 맞는 parameter를 못찾았지만 최초 argument가 1개인 경우
                    if (!TypeUtils.supports(methodArgs.get(0).paraminfo.getType())) { // 지원하는 primitive 타입이 아니라면 그 parameter각 POJO 객체라고 보고 그 객체의 field 중에서 찾는다.
                        try {
                            return Ognl.getValue(field0, methodArgs.get(0).value);
                        } catch (OgnlException e) {
                            throw new InvalidMapperElementException(String.format("Can't bind ':%s' parameter.", field0), e);
                        }
                    }
                }
                // 여기까지 왔으면 맞는 해당하는 argument 나 localVar가 없다는 뜻이다. 인터페이스 선언이나 xml mapper 선언에서 이름이 틀린 것이다.
                throw new InvalidMapperElementException(String.format("Can't bind ':%s' parameter.", field0));
            }
        }
    }

    /**
     * method arg, local var 에서 전달된 문자열이 나타내는 값을 찾아서 반환한다.
     * 전달되는 문자열은 "obj.field1.field2" 같은 형태로 전달될 수 있으며
     * 전달된 문자열이 field가 한 개이고 한 개의 pojo만 arg로 전달된 경우에 한하여 arg[0] 객체의 필드 중에서 같은 이름의 필드를 찾아서 반환한다.
     *
     * @param varFields 찾으려는 값을 표현하는 표현식 문자열. "obj.field1.field2" 형태의 문자열이다.
     * @return 찾은 값을 반환한다.
     */
    public Object getVarByFullFields(String varFields) {
        try {
            String[] fields = varFields.split("\\s*\\.\\s*"); // addPlaceholder()와 달리 여기서는 공백문자가 포함된 문자열이 올 수 있다.
            if (fields.length == 1)
                return getVarByField0(fields[0]);
            else if (fields.length > 1)
                return Ognl.getValue(varFields.substring(varFields.indexOf('.') + 1), getVarByField0(fields[0]));
            else
                throw new InvalidMapperElementException("Empty property is specified.");
        } catch (OgnlException e) {
            throw new InvalidMapperElementException(e);
        }
    }

    /**
     * 핸재 access 가능한 Method arg와 Local var 들 중에서 fields 가 가리키는 값의 타입(Class)을 찾아서 반환한다.
     * multi depth인 경우에도 알아서 찾아가고 해당 값을 찾아서 타입을 반환한다.
     *
     * @param fields 타입을 찾으려는 변수 이름이 "pojo.field1.field2" 인 경우 ["pojo", "field1", "field2"] 배열이 전달된다.
     * @return 발견된 변수의 Class 객체를 반환.
     */
    public Class<?> getTypeByFullFields(String[] fields) {
        // localVars는 스택과 같다. 뒤에 추가된 변수가 우선순위가 더 높으므로 뒤에서부터 검색한다.
        MethodImpl.ParamInfo paramInfo = null;
        for (int inx = localVars.size() - 1; inx >= 0; inx--) { // 뒤에 붙은 param들이 우선순위가 더 높기때문에 반드시 뒤에서부터 검색.
            VarInfo localVar = localVars.get(inx);
            if (fields[0].equals(localVar.paraminfo.getName())) {
                paramInfo = localVar.paraminfo;
                break;
            }
        }

        if (paramInfo != null) { // localVars에서 찾았다.
            return ReflectionUtils.getFieldType(paramInfo.getType(), fields, 1);
        } else { // localVars에 없으면 methodArgs에서 찾는다.
            Optional<VarInfo> methodArg = methodArgs.stream().filter(varInfo -> fields[0].equals(varInfo.paraminfo.getName())).findFirst();
            if (methodArg.isPresent()) {
                paramInfo = methodArg.get().paraminfo;
                return ReflectionUtils.getFieldType(paramInfo.getType(), fields, 1);
            } else { // methodArgs 에서도 없으면
                if (methodArgs.size() == 1) { // 맞는 parameter를 못찾았지만 최초 argument가 1개인 경우
                    if (!TypeUtils.supports(methodArgs.get(0).paraminfo.getType())) { // 지원하는 primitive 타입이 아니라면 그 parameter각 POJO 객체라고 보고 그 객체의 field 중에서 찾는다.
                        return ReflectionUtils.getFieldType(methodArgs.get(0).paraminfo.getType(), fields, 0);
                    }
                }
                // 여기까지 왔으면 맞는 해당하는 argument 나 localVar가 없다는 뜻이다. 인터페이스 선언이나 xml mapper 선언에서 이름이 틀린 것이다.
                throw new InvalidMapperElementException(String.format("Can't bind ':%s' parameter.", String.join(".", fields)));
            }
        }
    }
}
