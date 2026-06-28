import { Navigate, useParams } from 'react-router-dom';
import {
  isRuleCategoryId,
  rulesRouteForCategory,
} from '../pages/businessRuleCategories';

/** 旧 /business-rules/:categoryId → 模块内 canonical 路由 */
export function BusinessRulesLegacyRedirect() {
  const { categoryId } = useParams<{ categoryId: string }>();
  const raw = categoryId ?? '';
  const cat = isRuleCategoryId(raw) ? raw : 'demand';
  return <Navigate to={rulesRouteForCategory(cat)} replace />;
}
