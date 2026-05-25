// 今天吃什么插件
// 示例插件，演示如何编写插件

async function what_to_eat(params) {
  var foods = ["🍜 拉面", "🍚 蛋炒饭", "🍔 汉堡", "🍕 披萨", "🍲 麻辣烫", "🥟 饺子", "🍣 寿司"];
  var i = Math.floor(Math.random() * foods.length);
  return { success: true, food: foods[i] };
}

async function get_lucky_food(params) {
  var mood = params.mood || "开心";
  var luckyFoods = {
    "开心": "🍰 蛋糕",
    "难过": "🍦 冰淇淋",
    "生气": "🌶️ 麻辣火锅",
    "疲惫": "☕ 咖啡",
    "无聊": "🍿 爆米花"
  };
  var food = luckyFoods[mood] || "🍱 便当";
  return { success: true, food: food, mood: mood };
}

exports.what_to_eat = what_to_eat;
exports.get_lucky_food = get_lucky_food;

console.log("今天吃什么插件已加载");