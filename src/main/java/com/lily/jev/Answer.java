package com.lily.jev;

/**
 * @param choice     선택 질문의 키. 예/아니오일 때는 null
 * @param noul       예라고 볼 확률. 선택 질문이면 null
 * @param confidence 선택 질문의 확신. 예/아니오는 확률과 같다
 */
public record Answer(String choice, Double noul, double confidence) {
}
